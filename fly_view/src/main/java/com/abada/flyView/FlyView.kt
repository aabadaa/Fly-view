package com.abada.flyView

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@SuppressLint("ViewConstructor")
class FlyView constructor(
    context: Context,
    runRecomposeScope: CoroutineScope,
    private val onDragChanged: FlyView.(Int, Int) -> Unit,
    private val keyDispatcher: ((KeyEvent?) -> Boolean)? = null,
    private val content: @Composable () -> Unit,
) : AbstractComposeView(context, null, 0) {

    init {
        val lifecycleOwner = LifeCycle().also {
            it.performRestore(null)
            it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            //setViewTreeLifecycleOwner(it)
            ViewTreeLifecycleOwner.set(this, it)
        }

        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        val viewModelStore = ViewModelStore()
        ViewTreeViewModelStoreOwner.set(this) { viewModelStore }
        val recompose = Recomposer(AndroidUiDispatcher.CurrentThread)
        compositionContext = recompose
        runRecomposeScope.launch {
            recompose.runRecomposeAndApplyChanges()
        }
    }

    @Composable
    override fun Content() {
        Box(modifier = Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDragChanged(dragAmount.x.toInt(), dragAmount.y.toInt())
            }
        }) {
            content()
        }
        if (isAttachedToWindow) {
            Log.i(ContentValues.TAG, "flyContent: showed")
            createComposition()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {

        return keyDispatcher?.invoke(event) ?: super.dispatchKeyEvent(event)
    }

    override fun getAccessibilityClassName(): CharSequence = javaClass.name

    companion object {
        val infos = mutableMapOf<String, FlyViewInfo<Any>>()
    }
}


private class LifeCycle : SavedStateRegistryOwner {

    private var mLifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    private var mSavedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    override fun getLifecycle(): Lifecycle = mLifecycleRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        mLifecycleRegistry.handleLifecycleEvent(event)
    }

    fun performRestore(savedState: Bundle?) {
        mSavedStateRegistryController.performRestore(savedState)
    }

}

data class FlyViewInfo<T>(
    val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).also { it.windowAnimations = android.R.style.Animation },
    val keyDispatcher: ((KeyEvent?) -> Boolean)? = null,
    val controller: T? = null,
    val content: @Composable FlyViewScope<T>.() -> Unit,
)

class FlyViewScope<T>(
    params: WindowManager.LayoutParams,
    val removeView: () -> Unit,
    val controller: T?,
    private val updateLayoutParams: (WindowManager.LayoutParams) -> Unit,
) {
    var params: WindowManager.LayoutParams = params
        set(value) {
            updateLayoutParams(value)
            field = value
        }
}

