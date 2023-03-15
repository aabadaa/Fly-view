package com.abada.flyView

import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
/**
 * A holder to all information needed for the [FlyView]
 *  @property controller used to send bundles from anywhere to the [FlyView]
 *  @property params a [WindowManager.LayoutParams] that passed when adding the [FlyView]
 *  @property keyDispatcher this will be passed to the [FlyView] to handle key events
 *  @property content the content of the flyView
 *  @property flyView the [android.view.View] object that will be added to the [WindowManager]
 */
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

