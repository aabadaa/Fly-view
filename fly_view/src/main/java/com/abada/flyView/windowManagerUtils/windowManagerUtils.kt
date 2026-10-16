package com.abada.flyView.windowManagerUtils

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.core.AnchoredPosition
import com.abada.flyView.core.FlyRect
import com.abada.flyView.core.HAnchor
import com.abada.flyView.core.VAnchor

/** The views that are showing now, by key. Main thread only. */
internal val showedViews: MutableMap<String, FlyViewInfo<out FlyController>> = mutableMapOf()

/** The whole display, including the system bars. */
internal fun WindowManager.displayBounds(context: Context): Rect =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Rect(currentWindowMetrics.bounds)
    } else {
        val metrics = context.resources.displayMetrics
        Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

/** The display without the system bars and the display cutout. */
internal fun WindowManager.usableBounds(context: Context): Rect {
    val bounds = displayBounds(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insets = currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        bounds.left += insets.left
        bounds.top += insets.top
        bounds.right -= insets.right
        bounds.bottom -= insets.bottom
    }
    return bounds
}

internal fun Rect.toCore() = FlyRect(left, top, right, bottom)

/**
 * Converts between `WindowManager.LayoutParams.x/y` and absolute top-left positions.
 *
 * The meaning of `x` and `y` depends on the window gravity: an offset from the left/top edge,
 * from the right/bottom edge (growing inwards), or from the center (no gravity, the default).
 */
internal fun anchoredPosition(gravity: Int, layoutDirection: Int, frame: Rect, w: Int, h: Int): AnchoredPosition {
    val g = Gravity.getAbsoluteGravity(gravity, layoutDirection)
    val horizontal = when (g and Gravity.HORIZONTAL_GRAVITY_MASK) {
        Gravity.LEFT -> HAnchor.Start
        Gravity.RIGHT -> HAnchor.End
        else -> HAnchor.Center
    }
    val vertical = when (g and Gravity.VERTICAL_GRAVITY_MASK) {
        Gravity.TOP -> VAnchor.Top
        Gravity.BOTTOM -> VAnchor.Bottom
        else -> VAnchor.Center
    }
    return AnchoredPosition(horizontal, vertical, frame.toCore(), w, h)
}
