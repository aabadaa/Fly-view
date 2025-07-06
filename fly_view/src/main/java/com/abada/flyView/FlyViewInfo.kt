package com.abada.flyView

import android.content.Context
import android.content.res.Resources
import android.graphics.PixelFormat
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.abada.flyView.windowManagerUtils.animateTo
import com.abada.flyView.windowManagerUtils.removeFlyView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A holder to all information needed for the [FlyView]
 *
 * @param T The type of controller that extends [FlyController]
 * @property controller Used to send bundles from anywhere to the [FlyView]
 * @property onRemove Pass a lambda which will be called before removing the [FlyView]
 * @property params A [WindowManager.LayoutParams] that passed when adding the [FlyView]
 * @property keyDispatcher This will be passed to the [FlyView] to handle key events
 * @property content The content of the flyView
 * @property flyView The [android.view.View] object that will be added to the [WindowManager]
 * @property removeView Call it in your view to remove it
 */
data class FlyViewInfo<T : FlyController>(
    val controller: T,
    val onRemove: suspend FlyViewInfo<T>.() -> Unit = {},
    val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).also { it.windowAnimations = android.R.style.Animation },
    internal val keyDispatcher: ((KeyEvent?) -> Boolean?)? = null,
    internal val content: @Composable FlyViewInfo<T>.() -> Unit,
) {
    /** The actual FlyView instance that will be displayed */
    internal lateinit var flyView: FlyView

    /** Function to update the layout parameters of the view */
    private lateinit var updateLayoutParams: (WindowManager.LayoutParams) -> Unit

    /** Function to remove the view from the WindowManager */
    lateinit var removeView: () -> Unit

    /**
     * Internal method to add this FlyView to the WindowManager.
     * Sets up the view hierarchy, lifecycle management, and event handling.
     *
     * @param context Context used to create the view
     * @param key Unique identifier for this FlyView
     * @param windowManager The WindowManager to add the view to
     * @param onUpdateParams Callback invoked when layout parameters are updated
     */
    internal fun addToWindowManager(
        context: Context,
        key: String,
        windowManager: WindowManager,
        onUpdateParams: (WindowManager.LayoutParams) -> Unit = {},
    ) {
        val runRecomposeScope = CoroutineScope(AndroidUiDispatcher.CurrentThread)
        updateLayoutParams = {
            try {
                windowManager.updateViewLayout(flyView, it)
                onUpdateParams(it)
            } catch (e: IllegalArgumentException) {
                Log.e(javaClass.simpleName, "FlyViewInfo: trying to update removed view")
            }
        }
        removeView = {
            CoroutineScope(Dispatchers.Main). launch {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                updateLayoutParams()
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

    /**
     * Call this method to apply your modification to [params] object.
     * Updates the view's layout parameters in the WindowManager.
     */
    fun updateLayoutParams() = updateLayoutParams(params)

    /**
     * This method moves the fly view smoothly to a specific position.
     *
     * @param x Target X coordinate (defaults to current X position)
     * @param y Target Y coordinate (defaults to current Y position)
     * @param duration Animation duration in milliseconds
     */
    fun animateTo(x: Int = params.x, y: Int = params.y, duration: Long = 300) =
        params.animateTo(x, y, duration, ::updateLayoutParams)

    /**
     * Call this method to move the fly view to the border of the screen.
     * The view will move to the left or right edge based on its current position.
     *
     * @param duration Animation duration in milliseconds
     */
    fun goToScreenBorder(duration: Long = 300) {
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        animateTo(
            x = if (params.x < 0) -screenWidth / 2 else screenWidth / 2, duration = duration
        )
    }
}

