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
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.NoController

/**
 * define a FlyViewInfo and call this method to add it to the WindowManager
 *
 * @param context any context to create a [android.view.View] object
 * @param key a string to identify your view , it is used when you want to update it using [updateFlyView] method or remove it using [removeFlyView]
 * @param flyViewInfo the info of your fly view that holds your view and your controller
 */
fun <T : FlyController> WindowManager.addFlyInfo(
    context: Context,
    key: String,
    flyViewInfo: FlyViewInfo<T>
) {
    if (!Settings.canDrawOverlays(context)) context.startActivity(Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")
    ).also {
        it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })

    if (showedViews.containsKey(key)) return
    if (!showedViews.containsKey("close")) {
        val closeView = FlyViewInfo(NoController, params = WindowManager.LayoutParams(
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
        showedViews["close"] = closeView
        closeView.addToWindowManager(context, "close", this,) {}
    }
    showedViews[key] = flyViewInfo
    flyViewInfo.addToWindowManager(context, key, this) {
        if (it.y > Resources.getSystem().displayMetrics.heightPixels / 6) {
            showCloseView()
        } else {
            hideCloseView()
        }
    }
}


/**
 * call this method to remove views those are added by ``addFlyInfo``
 *
 * @param key the identifier of your fly view
 */
fun WindowManager.removeFlyView(key: String) {
    showedViews.remove(key)?.let {
        hideCloseView()
        it.flyView.run {
            removeView(rootView)
            this@removeFlyView.removeView(this)

        }
    }
    if (showedViews.size == 1) {
        removeFlyView("close")
    }
}


/**
 * call this method to pass data to your showed fly views by passing it in a bundle object
 *
 * @param key the identifier of your fly view
 */
fun updateFlyView(key: String, bundle: Bundle): Boolean =
    showedViews[key]?.controller?.update(bundle) == Unit

/**
 * this view is showed when the user drag the [com.abada.flyView.FlyView] to the bottom of the screen to remove it.
 *
 * assign your new content to fit your app style
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