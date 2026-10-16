package com.abada.flyView.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/**
 * A `ViewModel` for the session of a window: it is created the first time this runs, and cleared
 * (`onCleared`) when the branch of the composition that called it is left. Because it lives outside
 * the window, it can feed the parameters of the `FlyWindow` itself (`state`, `focusable`, `placement`...).
 *
 * Put it inside the branch that is composed while the window is open (`if (open) { ... }`) so leaving the
 * branch ends the session. Do the work that must finish after the window has gone inside a
 * `FlyDismissEffect`, before calling `dismiss()`.
 *
 * `viewModel()` inside the content of a `FlyWindow` is scoped to the window instead.
 */
@Composable
inline fun <reified VM : ViewModel> rememberFlyViewModel(key: Any? = null, noinline factory: () -> VM): VM =
    rememberFlyViewModel(VM::class.java, key, factory)

@Composable
fun <VM : ViewModel> rememberFlyViewModel(type: Class<VM>, key: Any?, factory: () -> VM): VM {
    val holder = remember(key) { FlyViewModelHolder() }
    return holder.get(type, factory)
}

class FlyViewModelHolder : RememberObserver {
    private val store = ViewModelStore()

    fun <VM : ViewModel> get(type: Class<VM>, factory: () -> VM): VM =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = factory() as T
        })[type]

    override fun onRemembered() {}
    override fun onForgotten() = store.clear()
    override fun onAbandoned() = store.clear()
}
