package com.abada.flyView.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class DismissReason { DragDrop, Back, External, Content }

/** What a dismiss effect can do. Ending the effect without calling any of them counts as [dismiss]. */
interface DismissScope {
    val reason: DismissReason

    /** The real dismissal */
    fun dismiss()

    /** Veto: the window stays (and snaps back if it was dropped on the dismiss zone) */
    fun cancel()
}

/**
 * Every way to remove a window is a request that goes through here.
 *
 * 1. If the view registered an effect, it runs and takes control (message, animation, confirmation...)
 *    and finishes with [DismissScope.dismiss] or [DismissScope.cancel].
 * 2. A dismissal is committed through [commit], where the owner updates its state.
 *
 * Requests while one is in progress are ignored. Not thread safe, use it from the main thread.
 */
class DismissCoordinator(
    private val scope: CoroutineScope,
    private val commit: (DismissReason) -> Unit,
    private val onCancelled: () -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
) {
    enum class State { Idle, Running, Committed }

    var state: State = State.Idle
        private set

    /** Registered by `FlyDismissEffect`, `null` if the view doesn't control its dismissal */
    var effect: (suspend DismissScope.() -> Unit)? = null

    /** @return `false` if a dismissal is already in progress */
    fun request(reason: DismissReason): Boolean {
        if (state != State.Idle) return false
        val effect = effect
        if (effect == null) {
            state = State.Committed
            commit(reason)
            return true
        }
        state = State.Running
        scope.launch {
            var finished = false
            val dismissScope = object : DismissScope {
                override val reason = reason
                override fun dismiss() {
                    if (finished) return
                    finished = true
                    state = State.Committed
                    commit(reason)
                }

                override fun cancel() {
                    if (finished) return
                    finished = true
                    state = State.Idle
                    onCancelled()
                }
            }
            try {
                effect(dismissScope)
                dismissScope.dismiss() // returned without choosing
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                dismissScope.cancel()
                onError(e)
            }
        }
        return true
    }

    /** Call when the window is shown again after it was hidden or dismissed */
    fun reset() {
        state = State.Idle
    }
}
