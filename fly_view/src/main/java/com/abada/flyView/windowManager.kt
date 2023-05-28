package com.abada.flyView

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val showedViews: MutableMap<String, FlyViewInfo<out FlyController>> = mutableMapOf()
/**
 * define a FlyViewInfo and call this method to add it to the WindowManager
 *
 * @param context any context to create a [android.view.View] object
 * @param key a string to identify your view when you want to update it using [updateFlyView] method
 * @param flyViewInfo the info of your fly view that holds your view and your controller
 */
fun <T : FlyController> WindowManager.addFlyInfo(
    context: Context,
    key: String,
    onRemove: () -> Unit = {},
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
        showedViews["close"] = closeView
        closeView.addToWindowManager(context, "close", this, {}, {})
    }
    showedViews[key] = flyViewInfo
    flyViewInfo.addToWindowManager(context, key, this, onRemove) {
        if (it.y > Resources.getSystem().displayMetrics.heightPixels / 6) {
            showCloseView()
        } else {
            hideCloseView()
        }
    }
}

fun WindowManager.removeFlyView(key: String) {
    showedViews.remove(key)?.let {
        hideCloseView()
        it.flyView.run {
            removeView(rootView)
            this@removeFlyView.removeView(this)

        }
    }
    if (showedViews.size == 1) showedViews.clear()
}

private fun WindowManager.hideCloseView() {
    showedViews["close"]?.let { closeView ->
        closeView.params.width = 0
        closeView.params.height = 0
        updateViewLayout(closeView.flyView, closeView.params)
    }
}

private fun WindowManager.showCloseView() {
    showedViews["close"]?.let { closeView ->
        closeView.params.width = WindowManager.LayoutParams.MATCH_PARENT
        closeView.params.height = Resources.getSystem().displayMetrics.heightPixels / 6
        updateViewLayout(closeView.flyView, closeView.params)
    }
}

fun updateFlyView(key: String, bundle: Bundle): Boolean =
    showedViews[key]?.controller?.update(bundle) == Unit

