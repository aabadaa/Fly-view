package com.abada.flyView

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A compose host that lives outside of an activity, so it provides the lifecycle, saved state,
 * view model store and back dispatcher owners that compose expects.
 *
 * Call [dispose] when the view is removed from the window to release everything.
 */
@SuppressLint("ViewConstructor")
internal class FlyView(
    context: Context,
    private val keyDispatcher: ((KeyEvent?) -> Boolean?)? = null,
    /** Compose to the parent composition instead of an own recomposer, like `Popup` and `Dialog` do */
    parentContext: CompositionContext? = null,
    /** Called for the back button when no back handler of the content is enabled */
    private val onBackFallback: (() -> Unit)? = null,
    private val content: @Composable () -> Unit,
) : AbstractComposeView(context, null, 0), SavedStateRegistryOwner {

    /** Called when the device configuration changes (rotation, folding, ...) */
    var onConfigChanged: (() -> Unit)? = null

    @Composable
    override fun Content() {
        content()
    }

    //lifecycle
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val savedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)

    private val onBackPressedDispatcherOwner = object : OnBackPressedDispatcherOwner {
        override val onBackPressedDispatcher = OnBackPressedDispatcher(Runnable { onBackFallback?.invoke() })
        override val lifecycle: Lifecycle
            get() = this@FlyView.lifecycle
    }

    // One store for the whole life of the view, so `viewModel()` keeps its state.
    private val store = ViewModelStore()

    private val recomposeScope = CoroutineScope(AndroidUiDispatcher.CurrentThread)
    private val recomposer = if (parentContext == null) Recomposer(AndroidUiDispatcher.CurrentThread) else null
    private var disposed = false

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        setViewTreeLifecycleOwner(this)
        setViewTreeOnBackPressedDispatcherOwner(onBackPressedDispatcherOwner)
        setViewTreeSavedStateRegistryOwner(this)
        setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore get() = store
        })
        if (recomposer != null) {
            compositionContext = recomposer
            recomposeScope.launch {
                recomposer.runRecomposeAndApplyChanges()
            }
        } else {
            compositionContext = parentContext
        }
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!disposed) lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onConfigChanged?.invoke()
    }

    /** Releases the composition, the recomposer and the view model store. Safe to call twice. */
    fun dispose() {
        if (disposed) return
        disposed = true
        disposeComposition()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        recomposer?.cancel()
        recomposeScope.cancel()
        store.clear()
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        keyDispatcher?.invoke(event)?.let { return it }
        val dispatcher = onBackPressedDispatcherOwner.onBackPressedDispatcher
        // consume back only when someone wants it, otherwise the key is not swallowed for nothing
        if (event?.keyCode == KeyEvent.KEYCODE_BACK && (onBackFallback != null || dispatcher.hasEnabledCallbacks())) {
            if (event.action == KeyEvent.ACTION_UP) dispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
