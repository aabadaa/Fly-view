package com.abada.flyView.overlay

import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

/**
 * The room a popup has in a layer: the screen without the system bars and the display cutout, in
 * screen coordinates. It changes with a rotation or a fold.
 */
@Composable
fun rememberFlyUsableScreen(): Rect {
    val context = LocalContext.current.applicationContext
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val r = wm.usableBounds(context)
        Rect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    }
}

/**
 * Where an element is on the screen (not in its window), for the anchor of a [FlyPopup], a menu or
 * a tooltip. [bounds] is worked out when you read it, so it is right even if the window moved since the
 * element was laid out (a drag or the lift above the keyboard move a window without a new layout).
 * Mark the element with [flyAnchor].
 */
@Stable
class FlyAnchor {
    internal var coordinates: LayoutCoordinates? = null
    internal var origin: (() -> Offset)? = null

    /** The element on the screen, [Rect.Zero] while it is not laid out */
    val bounds: Rect
        get() = coordinates?.screenBounds(origin ?: return Rect.Zero) ?: Rect.Zero
}

/**
 * Where [this] is on the screen: the window origin, asked now, plus the position in the window.
 * `positionOnScreen()` and `View.getLocationOnScreen` are not used: they lag behind a window that
 * was moved (by a drag, by the lift above the keyboard, or when it was first placed).
 */
internal fun LayoutCoordinates.screenBounds(origin: () -> Offset): Rect? {
    if (!isAttached) return null
    return Rect(positionInWindow() + origin(), size.toSize())
}

/** The origin of the window of the caller on the screen */
@Composable
internal fun rememberWindowOrigin(): () -> Offset {
    val fromWindow = LocalFlyWindowOrigin.current
    val view = LocalView.current
    return fromWindow ?: remember(view) {
        {
            val at = IntArray(2).also { view.getLocationOnScreen(it) }
            Offset(at[0].toFloat(), at[1].toFloat())
        }
    }
}

@Composable
fun rememberFlyAnchor(): FlyAnchor = remember { FlyAnchor() }

/** Makes this element the one that [anchor] follows */
fun Modifier.flyAnchor(anchor: FlyAnchor): Modifier = composed {
    anchor.origin = rememberWindowOrigin()
    onGloballyPositioned { anchor.coordinates = it }
}

/**
 * Reports where this element is on the screen every time it is laid out. A window that moves without
 * a new layout (a drag, the lift above the keyboard) is not reported again, so the value can be
 * stale: prefer [flyAnchor] unless you only need the size.
 */
fun Modifier.onScreenBounds(onBounds: (Rect) -> Unit): Modifier = composed {
    val origin = rememberWindowOrigin()
    onGloballyPositioned { it.screenBounds(origin)?.let(onBounds) }
}

/** What the content of a [FlyOverlayLayer] can see and do */
@Stable
interface FlyOverlayLayerScope {
    /** The room there is: the screen without system bars and cutout, in screen coordinates */
    val bounds: Rect

    /** The top of the keyboard in screen coordinates, `null` when it is closed or unknown. Best effort. */
    val keyboardTop: Int?

    /**
     * Places this element with its top-left corner at the position that [position] returns, in
     * screen coordinates (like an anchor from [onScreenBounds]). The layer may not start at the
     * corner of the screen; this takes care of it. The element is given the whole layer to measure in.
     */
    fun Modifier.placeOnScreen(position: (size: IntSize) -> IntOffset): Modifier
}

private class LayerScope : FlyOverlayLayerScope {
    var origin by mutableStateOf(Offset.Zero)
    override var bounds by mutableStateOf(Rect.Zero)
    override val keyboardTop: Int? get() = FlyOverlayHost.keyboardTop

    override fun Modifier.placeOnScreen(position: (size: IntSize) -> IntOffset): Modifier =
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                val at = position(IntSize(placeable.width, placeable.height))
                // read in the placement block: a new origin only places again, it does not measure again
                placeable.place(at.x - origin.x.toInt(), at.y - origin.y.toInt())
            }
        }
}

private var nextLayerId = 0

/**
 * A transparent window that covers the screen, for what has to leave its window: popups, menus,
 * tooltips, sheets. `Popup` needs an activity window and can't do that inside a [FlyWindow].
 *
 * It exists only while this call is in the composition and [visible] is `true`; leaving the
 * composition removes it right away. It keeps the size of the screen even when the keyboard shows
 * (`FlyIme.Ignore`). It is above every window that was added before it.
 *
 * A layer is composed in the composition that calls it, so the theme and the `CompositionLocal`s apply.
 * The overlay has to be started, otherwise nothing is shown.
 *
 * @param touchable `false` (default): touches go through to the app below, nothing in it can be
 * pressed. `true`: the layer takes every touch, also outside of the content. Use it when the user has
 * to press something, and dismiss from the touches that nothing in the content handles.
 * @param focusable `true` takes the focus: needed for text fields and the back button, and it takes
 * the keyboard and the back button from the app below. Only matters when [touchable] is `true`.
 * @param onBackPress called for the back button; needs [focusable]
 * @param key identifies the window; the default is one of its own, so layers never replace each other
 */
@Composable
fun FlyOverlayLayer(
    visible: Boolean = true,
    touchable: Boolean = false,
    focusable: Boolean = false,
    onBackPress: (() -> Unit)? = null,
    key: String? = null,
    content: @Composable FlyOverlayLayerScope.() -> Unit,
) {
    if (!visible || !FlyOverlayHost.isRunning) return
    val windowKey = key ?: remember { "flyView/layer/${nextLayerId++}" }
    val scope = remember { LayerScope() }
    scope.bounds = rememberFlyUsableScreen()
    FlyWindow(
        key = windowKey,
        // not rememberFlyWindowState: a layer has no position to remember
        state = remember(windowKey) { FlyWindowState(windowKey, FlyPosition.Center) },
        focusable = focusable && touchable,
        drag = FlyDrag.None,
        snap = FlySnap.None,
        dismissZone = null,
        placement = FlyPlacement.Fullscreen,
        ime = FlyIme.Ignore,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        configure = { params ->
            if (!touchable) params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        },
    ) {
        if (onBackPress != null && focusable && touchable) BackHandler(onBack = onBackPress)
        val windowOrigin = rememberWindowOrigin()
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { it.screenBounds(windowOrigin)?.let { r -> scope.origin = r.topLeft } }
        ) { scope.content() }
    }
}
