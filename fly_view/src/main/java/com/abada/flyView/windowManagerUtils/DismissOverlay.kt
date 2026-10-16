package com.abada.flyView.windowManagerUtils

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abada.flyView.DismissZone
import com.abada.flyView.FlyView
import kotlin.math.roundToInt

/**
 * The overlay that is shown at the bottom of the screen only while a view is being dragged.
 * It is a normal window that is added when the drag starts and removed when it ends.
 */
internal object DismissOverlay {
    private var view: FlyView? = null
    private var windowManager: WindowManager? = null
    private var hovered by mutableStateOf(false)

    fun show(context: Context, windowManager: WindowManager, zone: DismissZone) {
        if (view != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (windowManager.displayBounds(context).height() * zone.heightFraction).roundToInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).also {
            it.gravity = Gravity.BOTTOM
            it.windowAnimations = android.R.style.Animation_Toast
        }
        hovered = false
        val overlay = FlyView(context) { closeViewContent(hovered) }
        windowManager.addView(overlay, params)
        view = overlay
        this.windowManager = windowManager
    }

    fun setHovered(value: Boolean) {
        hovered = value
    }

    fun hide() {
        val overlay = view ?: return
        view = null
        try {
            windowManager?.removeView(overlay)
        } catch (e: IllegalArgumentException) {
            // already gone
        }
        windowManager = null
        overlay.dispose()
        hovered = false
    }
}
