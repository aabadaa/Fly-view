package com.abada.flyView

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
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
) : AbstractComposeView(context, null, 0), SavedStateRegistryOwner {
    // compose
    override var shouldCreateCompositionOnAttachedToWindow: Boolean = true

    @Composable
    override fun Content() {
        content()
        if (isAttachedToWindow) {
            Log.i(ContentValues.TAG, "flyContent: showed")
            createComposition()
            (lifecycle as LifecycleRegistry).currentState = Lifecycle.State.RESUMED
        }
    }

    //lifecycle
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private var savedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)

    private val onBackPressedDispatcherOwner = object : OnBackPressedDispatcherOwner {
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
        override val lifecycle: Lifecycle
            get() = this@FlyView.lifecycle
    }

    init {
        val lifecycleOwner = this.apply {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeOnBackPressedDispatcherOwner(onBackPressedDispatcherOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore
                get() = ViewModelStore()
        })
        val recompose = Recomposer(AndroidUiDispatcher.CurrentThread)
        compositionContext = recompose
        runRecomposeScope.launch {
            recompose.runRecomposeAndApplyChanges()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        onBackPressedDispatcherOwner.onBackPressedDispatcher.onBackPressed()
        return keyDispatcher?.invoke(event) ?: super.dispatchKeyEvent(event)
    }

}