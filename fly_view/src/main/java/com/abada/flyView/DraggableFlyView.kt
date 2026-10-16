package com.abada.flyView

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * This method makes your flyView draggable.
 *
 * - The view can't leave the screen, and it stays inside when its size or the screen changes.
 * - It works with any window gravity: `params.x` and `params.y` keep their window manager meaning.
 * - The whole window moves, even if only a small handle of it is wrapped in [DraggableFlyView].
 * - When the user lets go the view flies to the closest edge (the fling velocity is taken in account).
 * - If [FlyViewInfo.dismissZone] is not `null` the user can drop the view at the bottom to remove it.
 *
 * @param autoGoToBorder If it is `false` the view will stay where the user left it
 * @param duration The amount of time needed to move the view to the border of the screen in milliseconds
 * @param edgePadding The gap between the window and the screen edge when it is snapped
 * @param content The content of the draggable flyView
 */
@Composable
fun <T : FlyController> FlyViewInfo<T>.DraggableFlyView(
    autoGoToBorder: Boolean = true,
    duration: Long = 300,
    edgePadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    SideEffect {
        edgePaddingPx = with(density) { edgePadding.roundToPx() }
        snapDuration = duration
    }
    DisposableEffect(Unit) {
        draggableStarted()
        onDispose { draggableStopped() }
    }
    LaunchedEffect(autoGoToBorder) {
        if (autoGoToBorder) goToScreenBorder(duration)
    }
    Box(
        modifier = Modifier
            .pointerInput(autoGoToBorder) {
                val location = IntArray(2)

                // The window moves under the finger, so the pointer is measured in screen
                // coordinates, a local delta would be wrong as soon as the window moves.
                fun screenOf(local: Offset): Offset {
                    flyView.getLocationOnScreen(location)
                    return Offset(location[0] + local.x, location[1] + local.y)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    } ?: return@awaitEachGesture

                    beginDrag()
                    val startPointer = screenOf(slop.position)
                    val tracker = VelocityTracker()
                    tracker.addPosition(slop.uptimeMillis, startPointer)

                    drag(slop.id) { change ->
                        val pointer = screenOf(change.position)
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, pointer)
                        dragBy(pointer.x - startPointer.x, pointer.y - startPointer.y)
                    }
                    val velocity = tracker.calculateVelocity()
                    endDrag(velocity.x, velocity.y, autoGoToBorder)
                }
            },
    ) {
        content()
    }
}
