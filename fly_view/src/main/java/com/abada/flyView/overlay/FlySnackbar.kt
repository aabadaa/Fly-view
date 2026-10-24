package com.abada.flyView.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** How long a snackbar stays */
enum class FlySnackbarDuration(internal val millis: Long?) {
    Short(4000), Long(10000), Indefinite(null)
}

/** How a snackbar ended */
enum class FlySnackbarResult { Dismissed, ActionPerformed }

/** A snackbar that is showing. [performAction] and [dismiss] end it. */
@Stable
class FlySnackbarData internal constructor(
    val message: String,
    val actionLabel: String?,
    val duration: FlySnackbarDuration,
    private val continuation: CancellableContinuation<FlySnackbarResult>,
) {
    fun performAction() {
        if (continuation.isActive) continuation.resume(FlySnackbarResult.ActionPerformed)
    }

    fun dismiss() {
        if (continuation.isActive) continuation.resume(FlySnackbarResult.Dismissed)
    }
}

/**
 * The state of a [FlySnackbarHost]. Like Material's: [showSnackbar] suspends until the snackbar is
 * gone, one at a time, the others wait their turn.
 *
 * ```
 * val result = snackbars.showSnackbar("Note deleted", actionLabel = "Undo")
 * if (result == FlySnackbarResult.ActionPerformed) undo()
 * ```
 */
@Stable
class FlySnackbarState {
    private val mutex = Mutex()

    /** The snackbar that is showing, `null` when there is none */
    var current by mutableStateOf<FlySnackbarData?>(null)
        private set

    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        duration: FlySnackbarDuration =
            if (actionLabel == null) FlySnackbarDuration.Short else FlySnackbarDuration.Long,
    ): FlySnackbarResult = mutex.withLock {
        try {
            suspendCancellableCoroutine { continuation ->
                current = FlySnackbarData(message, actionLabel, duration, continuation)
            }
        } finally {
            current = null
        }
    }
}

@Composable
fun rememberFlySnackbarState(): FlySnackbarState = remember { FlySnackbarState() }

/** Defaults of [FlySnackbarHost] */
object FlySnackbarDefaults {
    val ContainerColor = Color(0xFF322F35)
    val ContentColor = Color(0xFFF5EFF7)
    val ActionColor = Color(0xFFD0BCFF)

    /** The snackbar that [FlySnackbarHost] draws unless you give it your own content */
    @Composable
    fun Snackbar(data: FlySnackbarData, modifier: Modifier = Modifier) {
        val shape = RoundedCornerShape(4.dp)
        Row(
            modifier
                .widthIn(min = 288.dp, max = 560.dp)
                .shadow(6.dp, shape)
                .background(ContainerColor, shape)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                data.message,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 14.dp),
                style = TextStyle(color = ContentColor, fontSize = 14.sp, lineHeight = 20.sp),
            )
            if (data.actionLabel != null) {
                BasicText(
                    data.actionLabel,
                    modifier = Modifier
                        .clickable { data.performAction() }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    style = TextStyle(color = ActionColor, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/**
 * Shows the snackbars of [state] above any window, at the bottom of the screen. It is a small window
 * docked to the bottom, so the rest of the screen stays touchable, and it exists only while a snackbar
 * shows. It moves up when the keyboard is open (best effort).
 *
 * The snackbar is composed in the composition of the host, so your theme applies to [snackbar].
 * Put one host near the top of your overlay, for example around your windows.
 *
 * @param snackbar draws one snackbar; call `data.performAction()` / `data.dismiss()` from it
 */
@Composable
fun FlySnackbarHost(
    state: FlySnackbarState,
    snackbar: @Composable (FlySnackbarData) -> Unit = { FlySnackbarDefaults.Snackbar(it) },
) {
    val current = state.current ?: return
    if (!FlyOverlayHost.isRunning) return
    LaunchedEffect(current) {
        current.duration.millis?.let {
            delay(it)
            current.dismiss()
        }
    }
    val density = LocalDensity.current
    val usable = rememberFlyUsableScreen()
    val keyboardTop = FlyOverlayHost.keyboardTop
    val base = 16.dp
    val margin = with(density) {
        val lift = if (keyboardTop != null) (usable.bottom - keyboardTop).coerceAtLeast(0f).toDp() else 0.dp
        base + lift
    }
    val key = "flyView/snackbar"
    FlyWindow(
        key = key,
        state = remember { FlyWindowState(key, FlyPosition.Center) },
        drag = FlyDrag.None,
        snap = FlySnap.None,
        dismissZone = null,
        placement = FlyPlacement.Docked(FlyEdge.Bottom, FlySize.Wrap, margin = margin),
        ime = FlyIme.Ignore,
    ) {
        snackbar(current)
    }
}
