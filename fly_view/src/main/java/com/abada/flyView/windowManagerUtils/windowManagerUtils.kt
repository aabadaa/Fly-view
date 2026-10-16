package com.abada.flyView.windowManagerUtils

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.res.Resources
import android.util.Property
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.NoController

/** The views added by the user, the close view is tracked separately so keys can't collide with it */
internal val showedViews: MutableMap<String, FlyViewInfo<out FlyController>> = mutableMapOf()

/** The overlay shown at the bottom of the screen while a view is dragged to the removal zone */
internal var closeView: FlyViewInfo<NoController>? = null

private const val CLOSE_ZONE_DIVISOR = 6

/** Height of the bottom zone in which a dragged view is removed, and of the close view */
internal fun closeZoneHeight(): Int = Resources.getSystem().displayMetrics.heightPixels / CLOSE_ZONE_DIVISOR

/** Whether a view whose layout y is [y] is in the removal zone */
internal fun isInCloseZone(y: Int): Boolean = y > closeZoneHeight()

internal fun hideCloseView() {
    closeView?.let { closeView ->
        closeView.params.apply {
            width = 0
            height = 0
        }
        closeView.updateLayoutParams()
    }
}

internal fun showCloseView() {
    closeView?.let { closeView ->
        closeView.params.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = closeZoneHeight()
        }
        closeView.updateLayoutParams()
    }
}


internal fun WindowManager.LayoutParams.animateTo(
    x: Int = this.x,
    y: Int = this.y,
    duration: Long = 300,
    onUpdate: () -> Unit
): ObjectAnimator {
    val xProperty = object : Property<WindowManager.LayoutParams, Int>(Int::class.java, "x") {
        override fun get(params: WindowManager.LayoutParams): Int = params.x
        override fun set(params: WindowManager.LayoutParams, value: Int) {
            params.x = value
        }
    }

    val yProperty = object : Property<WindowManager.LayoutParams, Int>(Int::class.java, "y") {
        override fun get(params: WindowManager.LayoutParams): Int = params.y
        override fun set(params: WindowManager.LayoutParams, value: Int) {
            params.y = value
        }
    }

    val animator = ObjectAnimator.ofPropertyValuesHolder(
        this, PropertyValuesHolder.ofInt(xProperty, x), PropertyValuesHolder.ofInt(yProperty, y)
    ).apply {
        this.duration = duration
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            onUpdate()
        }
    }
    animator.start()
    return animator
}