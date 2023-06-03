package com.abada.flyView

import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.abada.flyView.windowManagerUtils.removeFlyView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A holder to all information needed for the [FlyView]
 *  @property controller used to send bundles from anywhere to the [FlyView]
 *  @property onRemove pass a lambda which will be called before removing the [FlyView]
 *  @property params a [WindowManager.LayoutParams] that passed when adding the [FlyView]
 *  @property keyDispatcher this will be passed to the [FlyView] to handle key events
 *  @property content the content of the flyView
 *  @property flyView the [android.view.View] object that will be added to the [WindowManager]
 */
data class FlyViewInfo<T : FlyController>(
    val controller: T,
    val onRemove: suspend FlyViewInfo<T>.() -> Unit = {},
    private var params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).also { it.windowAnimations = android.R.style.Animation },
    internal val keyDispatcher: ((KeyEvent?) -> Boolean)? = null,
    internal val content: @Composable FlyViewInfo<T>.() -> Unit,
) {
    internal lateinit var flyView: FlyView
    private lateinit var updateLayoutParams: (WindowManager.LayoutParams) -> Unit
    lateinit var removeView: () -> Unit

    fun addToWindowManager(
        context: Context,
        key: String,
        windowManager: WindowManager,
        onUpdateParams: (WindowManager.LayoutParams) -> Unit = {},
    ) {
        val runRecomposeScope = CoroutineScope(AndroidUiDispatcher.CurrentThread)
        updateLayoutParams = {
            windowManager.updateViewLayout(flyView, it)
            onUpdateParams(it)
        }
        removeView = {
            runRecomposeScope.launch {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windowManager.updateViewLayout(flyView, params)
                onUpdateParams(params)
                onRemove(this@FlyViewInfo)
                delay(100)// if there is an animation the app will crash , so I delayed a little to wait the animation to finih
                runRecomposeScope.cancel()
                windowManager.removeFlyView(key)
            }
        }
        flyView = FlyView(
            context = context,
            runRecomposeScope = runRecomposeScope,
            keyDispatcher = keyDispatcher,
            content = {
                content()
            }
        )
        windowManager.addView(flyView, params)
    }

    fun params(edit: (WindowManager.LayoutParams) -> WindowManager.LayoutParams) {
        params = edit(params)
        updateLayoutParams(params)
    }
}

