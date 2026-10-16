package com.abada.flyView.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.abada.flyView.core.EdgeSide
import com.abada.flyView.core.FlyPoint

/**
 * The position of a window and what the user is doing with it. Use one state per mode when
 * the window remembers a different position for each of them (an icon and an editor, for example):
 * switching the state of a `FlyWindow` animates it to the position of the other one.
 *
 * Positions are absolute top-left pixels, they do not depend on the window gravity or size.
 */
@Stable
class FlyWindowState internal constructor(
    val key: String,
    internal val initial: FlyPosition,
) {
    /** The last position of the window, `null` before it was ever shown */
    var position: FlyPoint? by mutableStateOf(null)
        internal set

    /** The edge the window is snapped to, if any */
    var snappedSide: EdgeSide? by mutableStateOf(null)
        internal set

    var isDragging: Boolean by mutableStateOf(false)
        internal set

    var isOverDismissZone: Boolean by mutableStateOf(false)
        internal set

    internal var runtime: FlyWindowRuntime? = null

    /** Set when the position is persisted on disk */
    internal var persistContext: android.content.Context? = null

    internal fun save() {
        val context = persistContext ?: return
        val p = position ?: return
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putInt("$key.x", p.x).putInt("$key.y", p.y)
            .putInt("$key.side", snappedSide?.ordinal ?: -1)
            .apply()
    }

    internal fun load(context: android.content.Context) {
        persistContext = context
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (!prefs.contains("$key.x")) return
        position = FlyPoint(prefs.getInt("$key.x", 0), prefs.getInt("$key.y", 0))
        snappedSide = prefs.getInt("$key.side", -1).takeIf { it >= 0 }?.let { EdgeSide.entries[it] }
    }

    /** Moves the window to [x], [y] (absolute top-left pixels) with an animation. Does nothing if it is not shown. */
    fun animateTo(x: Int, y: Int, durationMillis: Long = 300) {
        runtime?.animateTo(this, x, y, durationMillis)
    }

    /** Moves the window to the closest horizontal edge */
    fun snapToEdge(durationMillis: Long = 300) {
        runtime?.snapNow(this, durationMillis)
    }

    internal fun savedPosition(): FlyPoint? = position

    internal companion object {
        const val PREFS = "flyView_positions"
    }
}

/**
 * The window state of [key]. The same key gives the same state while the process lives,
 * so a window that comes back is where it was.
 *
 * @param initial where the window starts the first time
 * @param persist keep the position on disk, so it survives a process restart
 */
@Composable
fun rememberFlyWindowState(
    key: String,
    initial: FlyPosition = FlyPosition.Center,
    persist: Boolean = false,
): FlyWindowState {
    val context = LocalContext.current.applicationContext
    return remember(key) { FlyOverlayHost.stateFor(context, key, initial, persist) }
}
