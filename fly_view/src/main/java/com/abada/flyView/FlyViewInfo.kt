package com.abada.flyView

import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable

data class FlyViewInfo<T : FlyController>(
    val controller: T,
    internal val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).also { it.windowAnimations = android.R.style.Animation },
    internal val keyDispatcher: ((KeyEvent?) -> Boolean)? = null,
    internal val content: @Composable FlyViewScope.() -> Unit,
) {
    internal lateinit var flyView: FlyView
}

