package com.abada.flyView.overlay

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker

/**
 * Makes this area drag the whole window it is in. With `drag = FlyDrag.Whole` the whole window
 * already drags, with `FlyDrag.HandleOnly` only the areas that use this modifier do.
 */
fun Modifier.flyDragHandle(): Modifier = composed {
    val window = LocalFlyWindow.current as FlyWindowScopeImpl
    val runtime = window.runtime
    pointerInput(runtime) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // measured from the touch down, not from where the slop is crossed, so the window
            // does not trail the finger by the slop distance
            val downPointer = runtime.pointerOnScreen()
            val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
            if (!runtime.canDrag) return@awaitEachGesture

            // The window moves under the finger, so the pointer is the raw screen position of the touch
            // event: a local position (or the window location plus one) is wrong as soon as the window moves.
            runtime.beginDrag()
            val slopPointer = runtime.pointerOnScreen() ?: return@awaitEachGesture
            val startPointer = downPointer ?: slopPointer
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, startPointer)
            tracker.addPosition(slop.uptimeMillis, slopPointer)
            runtime.dragBy(slopPointer.x - startPointer.x, slopPointer.y - startPointer.y)
            drag(slop.id) { change ->
                val pointer = runtime.pointerOnScreen() ?: return@drag
                change.consume()
                tracker.addPosition(change.uptimeMillis, pointer)
                runtime.dragBy(pointer.x - startPointer.x, pointer.y - startPointer.y)
            }
            val velocity = tracker.calculateVelocity()
            runtime.endDrag(velocity.x, velocity.y)
        }
    }
}
