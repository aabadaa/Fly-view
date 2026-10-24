package com.abada.flyView.overlay

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

/**
 * How far this window reaches under the keyboard, in pixels: `0` when it is clear of it.
 *
 * A floating window with `ime = FlyIme.Avoid` is lifted above the keyboard, and the overlap it
 * reports goes back to `0` as soon as that is done. What stays is a window too tall for the room that
 * is left (shrink your field by that much). So an overlap is only reported once it has lasted
 * [graceMillis], the time the window needs to be lifted, and goes back to `0` at once.
 *
 * Read it inside a [FlyWindow]; it is the inset of that window.
 */
@Composable
fun rememberFlyImeOverlap(graceMillis: Long = 200): State<Int> {
    val raw = WindowInsets.ime.getBottom(LocalDensity.current)
    val settled = remember { mutableIntStateOf(0) }
    LaunchedEffect(raw) {
        if (raw > 0) delay(graceMillis)
        settled.intValue = raw
    }
    return settled
}
