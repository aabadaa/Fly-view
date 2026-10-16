package com.abada.flyView.windowManagerUtils

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.NoController

/**
 * Define a FlyViewInfo and call this method to add it to the WindowManager.
 * This function handles overlay permission checking and automatically shows a close view
 * when multiple FlyViews are displayed.
 *
 * @param context Any context to create a [android.view.View] object
 * @param key A string to identify your view, used when you want to update it using [updateFlyView] method or remove it using [removeFlyView]
 * @param flyViewInfo The info of your fly view that holds your view and your controller
 * @return true if the view was added, false if the overlay permission is missing
 * (the permission screen is opened) or a view with the same [key] is already shown
 */
fun <T : FlyController> WindowManager.addFlyInfo(
    context: Context,
    key: String,
    flyViewInfo: FlyViewInfo<T>
): Boolean {
    if (!Settings.canDrawOverlays(context)) {
        context.startActivity(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        // adding the view without the permission would throw
        return false
    }

    if (showedViews.containsKey(key)) return false
    if (closeView == null) {
        val newCloseView = FlyViewInfo(NoController, params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).also {
            it.y = Resources.getSystem().displayMetrics.heightPixels
            it.width = 0
            it.height = 0
        }) {
            closeViewContent()
        }
        closeView = newCloseView
        newCloseView.addToWindowManager(context, "close", this) {}
    }
    showedViews[key] = flyViewInfo
    flyViewInfo.addToWindowManager(context, key, this) {
        if (isInCloseZone(it.y)) {
            showCloseView()
        } else {
            hideCloseView()
        }
    }
    return true
}


/**
 * Call this method to remove views those are added by `addFlyInfo`.
 * Automatically manages the close view visibility and cleans up resources.
 *
 * @param key The identifier of your fly view
 */
fun WindowManager.removeFlyView(key: String) {
    showedViews.remove(key)?.let {
        hideCloseView()
        removeView(it.flyView)
    }
    if (showedViews.isEmpty()) {
        closeView?.let {
            closeView = null
            removeView(it.flyView)
        }
    }
}


/**
 * Call this method to pass data to your showed fly views by passing it in a bundle object.
 * Updates the controller of the specified FlyView with the provided data.
 *
 * @param key The identifier of your fly view
 * @param bundle The data bundle to pass to the FlyView's controller
 * @return true if the view was found and updated, false otherwise
 */
fun updateFlyView(key: String, bundle: Bundle): Boolean =
    showedViews[key]?.controller?.update(bundle) == Unit

/**
 * This view is showed when the user drags the [com.abada.flyView.FlyView] to the bottom of the screen to remove it.
 *
 * Assign your new content to fit your app style. The default implementation shows a delete icon
 * with a gradient background in the bottom portion of the screen.
 */
var closeViewContent: @Composable () -> Unit = {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(.3f)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Gray)))
    ) {
        Icon(
            modifier = Modifier.align(Alignment.Center),
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete"
        )
    }
}