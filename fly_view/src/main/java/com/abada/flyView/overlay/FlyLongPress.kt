package com.abada.flyView.overlay

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Calls [onLongPress] when this element is pressed for the long press time without moving, with a
 * haptic tick if [haptic]. Unlike `combinedClickable` it can sit next to a `clickable` of the same
 * element: it looks at the touch first, and after a long press it consumes the rest of the gesture, so the
 * element does not receive the click. A press that moves, or ends early, is left alone.
 */
fun Modifier.flyLongPress(
    enabled: Boolean = true,
    haptic: Boolean = true,
    onLongPress: () -> Unit,
): Modifier = composed {
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val slop = LocalViewConfiguration.current.touchSlop
    val feedback = LocalHapticFeedback.current
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    if (!enabled) Modifier
    else Modifier.pointerInput(timeout, slop, haptic) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // null = the time ran out while still pressed in place
            val ended = withTimeoutOrNull(timeout) {
                var over = false
                while (!over) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id }
                    over = change == null || change.changedToUpIgnoreConsumed() ||
                            (change.position - down.position).getDistance() > slop
                }
                true
            }
            if (ended != null) return@awaitEachGesture
            if (haptic) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnLongPress()
            // the rest of the press is ours: the element must not take its release for a click
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
