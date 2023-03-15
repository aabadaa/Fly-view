package com.abada.flyView

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
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
internal class FlyView constructor(
    context: Context,
    runRecomposeScope: CoroutineScope,
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
        content()
        if (isAttachedToWindow) {
            Log.i(ContentValues.TAG, "flyContent: showed")
            createComposition()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {

        return keyDispatcher?.invoke(event) ?: super.dispatchKeyEvent(event)
    }

    override fun getAccessibilityClassName(): CharSequence = javaClass.name
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