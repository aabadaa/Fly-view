package com.abada.flyView.windowManagerUtils

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo

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

/**
 * Converts between `WindowManager.LayoutParams.x/y` and absolute top-left positions.
 *
 * The meaning of `x` and `y` depends on the window gravity: an offset from the left/top edge,
 * from the right/bottom edge (growing inwards), or from the center (no gravity, the default).
 */
internal class WindowPosition(
    gravity: Int,
    layoutDirection: Int,
    private val frame: Rect,
    private val w: Int,
    private val h: Int,
) {
    private val g = Gravity.getAbsoluteGravity(gravity, layoutDirection)
    private val horizontal = g and Gravity.HORIZONTAL_GRAVITY_MASK
    private val vertical = g and Gravity.VERTICAL_GRAVITY_MASK

    /** `true` if a growing `params.x` moves the window to the left */
    val flipX get() = horizontal == Gravity.RIGHT

    /** `true` if a growing `params.y` moves the window up */
    val flipY get() = vertical == Gravity.BOTTOM

    fun absX(x: Int): Int = when (horizontal) {
        Gravity.LEFT -> frame.left + x
        Gravity.RIGHT -> frame.right - w - x
        else -> frame.left + (frame.width() - w) / 2 + x
    }

    fun absY(y: Int): Int = when (vertical) {
        Gravity.TOP -> frame.top + y
        Gravity.BOTTOM -> frame.bottom - h - y
        else -> frame.top + (frame.height() - h) / 2 + y
    }

    fun paramX(absX: Int): Int = when (horizontal) {
        Gravity.LEFT -> absX - frame.left
        Gravity.RIGHT -> frame.right - w - absX
        else -> absX - frame.left - (frame.width() - w) / 2
    }

    fun paramY(absY: Int): Int = when (vertical) {
        Gravity.TOP -> absY - frame.top
        Gravity.BOTTOM -> frame.bottom - h - absY
        else -> absY - frame.top - (frame.height() - h) / 2
    }
}
