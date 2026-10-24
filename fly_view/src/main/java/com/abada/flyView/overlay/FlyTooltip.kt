package com.abada.flyView.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abada.flyView.core.PopupAlignment
import com.abada.flyView.core.PopupSide
import kotlinx.coroutines.delay

/** Shows tooltips. Reach the one around you with [LocalFlyTooltips]. */
interface FlyTooltips {
    /** Shows [text] next to [anchor] (in screen coordinates), replacing the tooltip that is showing. */
    fun show(text: String, anchor: Rect)

    /** Hides the tooltip that is showing, if any */
    fun hide()

    companion object {
        /** Does nothing: what [LocalFlyTooltips] holds where there is no [FlyTooltipHost] */
        val None: FlyTooltips = object : FlyTooltips {
            override fun show(text: String, anchor: Rect) {}
            override fun hide() {}
        }
    }
}

/**
 * The tooltips of the enclosing [FlyTooltipHost]. Without a host it is [FlyTooltips.None]:
 * showing a tooltip does nothing, so a composable can use [flyTooltip] whether it is in a host or not.
 */
val LocalFlyTooltips = staticCompositionLocalOf { FlyTooltips.None }

/** A tooltip on the screen: what it says and the area it is about, in screen coordinates */
@Stable
class FlyTooltip internal constructor(val text: String, val anchor: Rect)

/** The state of a [FlyTooltipHost]. Use it to show a tooltip from code: `state.show("Saved", rect)`. */
@Stable
class FlyTooltipState : FlyTooltips {
    /** The tooltip that is showing, `null` when there is none */
    var current: FlyTooltip? by mutableStateOf(null)
        private set

    override fun show(text: String, anchor: Rect) {
        current = FlyTooltip(text, anchor)
    }

    override fun hide() {
        current = null
    }

    internal fun hide(tooltip: FlyTooltip) {
        if (current === tooltip) current = null
    }
}

@Composable
fun rememberFlyTooltipState(): FlyTooltipState = remember { FlyTooltipState() }

/** Defaults of [FlyTooltipHost] */
object FlyTooltipDefaults {
    /** How long a tooltip stays on the screen */
    const val DurationMillis = 1800L

    /** The container color of Material 3's plain tooltip (`inverseSurface`) */
    val ContainerColor = Color(0xFF322F35)

    /** The text color of Material 3's plain tooltip (`inverseOnSurface`) */
    val ContentColor = Color(0xFFF5EFF7)

    val Shape: Shape = RoundedCornerShape(8.dp)

    /** The bubble that [FlyTooltipHost] draws unless you give it your own content */
    @Composable
    fun Bubble(
        text: String,
        modifier: Modifier = Modifier,
        containerColor: Color = ContainerColor,
        contentColor: Color = ContentColor,
        shape: Shape = Shape,
    ) {
        BasicText(
            text = text,
            modifier = modifier
                .shadow(4.dp, shape)
                .background(containerColor, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(color = contentColor, fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

/**
 * Makes tooltips work inside [content], including inside a [FlyWindow]. `TooltipBox`, `Popup` and
 * `DropdownMenu` need an activity window and can't show in a floating one, so the tooltip is drawn in a
 * [FlyOverlayLayer] that takes no touches, composed only while a tooltip shows.
 *
 * Put it around the windows: the windows (and anything composed inside them) reach it with
 * [LocalFlyTooltips] and [flyTooltip]. The tooltip is composed in the composition of the host, so
 * your theme applies to [tooltip].
 *
 * It sits above the anchor, or below it if there is no room, inside the screen, pops in and goes away
 * after [durationMillis]. It does nothing while the overlay is not started.
 *
 * @param state show a tooltip from code with it
 * @param durationMillis how long a tooltip stays
 * @param tooltip draws the bubble, it is placed and animated for you. [FlyTooltipDefaults.Bubble] by default.
 */
@Composable
fun FlyTooltipHost(
    state: FlyTooltipState = rememberFlyTooltipState(),
    durationMillis: Long = FlyTooltipDefaults.DurationMillis,
    tooltip: @Composable (FlyTooltip) -> Unit = { FlyTooltipDefaults.Bubble(it.text) },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFlyTooltips provides state) { content() }
    val current = state.current ?: return
    LaunchedEffect(current, durationMillis) {
        delay(durationMillis)
        state.hide(current)
    }
    FlyOverlayLayer {
        val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
        AnchoredContent(
            anchor = current.anchor,
            side = PopupSide.Top,
            alignment = PopupAlignment.Center,
            offset = IntOffset.Zero,
            gap = margin,
            margin = margin,
        ) { tooltip(current) }
    }
}

/**
 * Shows [text] in a tooltip next to this element on a long press, with a haptic tick. It needs a
 * [FlyTooltipHost] around it and does nothing without one. After a long press the element does not
 * receive the click (see [flyLongPress]).
 *
 * The position of the element is read when the press is long enough, so it is right even if the window
 * moved since it was laid out.
 */
fun Modifier.flyTooltip(text: String, enabled: Boolean = true): Modifier = composed {
    val tooltips = LocalFlyTooltips.current
    val anchor = rememberFlyAnchor()
    val currentText by rememberUpdatedState(text)
    flyAnchor(anchor)
        .flyLongPress(enabled = enabled && tooltips !== FlyTooltips.None) {
            anchor.bounds.takeIf { it != Rect.Zero }?.let { tooltips.show(currentText, it) }
        }
}
