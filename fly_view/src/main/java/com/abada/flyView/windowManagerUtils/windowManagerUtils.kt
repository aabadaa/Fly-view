package com.abada.flyView.windowManagerUtils

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.res.Resources
import android.util.Property
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo

internal val showedViews: MutableMap<String, FlyViewInfo<out FlyController>> = mutableMapOf()

internal fun hideCloseView() {
    showedViews["close"]?.let { closeView ->
        closeView.params.apply {
            width = 0
            height = 0
        }
        closeView.updateLayoutParams()
    }
}

internal fun showCloseView() {
    showedViews["close"]?.let { closeView ->
        closeView.params.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = Resources.getSystem().displayMetrics.heightPixels / 6
        }
        closeView.updateLayoutParams()
    }
}


internal fun WindowManager.LayoutParams.animateTo(
    x: Int = this.x,
    y: Int = this.y,
    duration: Long = 300,
    onUpdate: () -> Unit
) {
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
}