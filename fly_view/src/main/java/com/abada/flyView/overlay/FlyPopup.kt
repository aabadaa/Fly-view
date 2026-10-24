package com.abada.flyView.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abada.flyView.core.FlyRect
import com.abada.flyView.core.FlySizePx
import com.abada.flyView.core.PopupAlignment
import com.abada.flyView.core.PopupSide
import com.abada.flyView.core.placePopup
import kotlin.math.roundToInt

typealias FlyPopupSide = PopupSide
typealias FlyPopupAlignment = PopupAlignment

internal fun Rect.toFlyRect() = FlyRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

/**
 * Places [content] next to [anchor] inside a layer: the core placement (`placePopup`) with the screen
 * origin and the keyboard handled. Shared by popups, menus, tooltips and the text toolbar.
 */
@Composable
internal fun FlyOverlayLayerScope.AnchoredContent(
    anchor: Rect,
    side: FlyPopupSide,
    alignment: FlyPopupAlignment,
    offset: IntOffset,
    gap: Int,
    margin: Int,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxWidth = with(density) { (bounds.width - 2 * margin).coerceAtLeast(0f).toDp() }
    val maxHeight = with(density) { (bounds.height - 2 * margin).coerceAtLeast(0f).toDp() }
    Box(
        Modifier
            .placeOnScreen { size ->
                val placement = placePopup(
                    anchor = anchor.toFlyRect(),
                    size = FlySizePx(size.width, size.height),
                    bounds = bounds.toFlyRect(),
                    side = side,
                    alignment = alignment,
                    offsetX = offset.x,
                    offsetY = offset.y,
                    gap = gap,
                    margin = margin,
                    keyboardTop = keyboardTop,
                )
                IntOffset(placement.position.x, placement.position.y)
            }
            .sizeIn(maxWidth = maxWidth, maxHeight = maxHeight)
            .popIn(anchor)
    ) { content() }
}

/** Fades and scales in a little, once per [key] */
internal fun Modifier.popIn(key: Any): Modifier = composed {
    val appear = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        appear.animateTo(1f, spring(dampingRatio = .7f, stiffness = Spring.StiffnessMedium))
    }
    graphicsLayer {
        alpha = appear.value.coerceIn(0f, 1f)
        scaleX = .85f + .15f * appear.value
        scaleY = .85f + .15f * appear.value
    }
}

/**
 * A popup next to [anchor] that works inside a [FlyWindow], where `Popup` needs an activity window.
 * Compose it while it is open; leaving the composition closes it.
 *
 * It is drawn in a touchable [FlyOverlayLayer], so a touch outside of [content] calls [onDismissRequest]
 * and does not reach what is below (like a dialog). With [focusable] the back button calls it too, and
 * text fields in [content] can take the keyboard.
 *
 * It goes on [side] of the anchor, flips to the other side when there is no room, and stays inside the
 * screen (system bars, cutout and keyboard excluded).
 *
 * @param anchor the element it is about, in screen coordinates: see [FlyAnchor]
 * @param onDismissRequest the user touched outside (or pressed back, with [focusable]): update your state
 * @param side which side of the anchor it goes
 * @param alignment how it lines up with the edge of the anchor
 * @param offset moves it after the side is chosen, not mirrored when the popup flips
 * @param focusable takes the focus: the back button and the keyboard
 */
@Composable
fun FlyPopup(
    anchor: Rect,
    onDismissRequest: () -> Unit,
    side: FlyPopupSide = PopupSide.Bottom,
    alignment: FlyPopupAlignment = PopupAlignment.Start,
    offset: DpOffset = DpOffset.Zero,
    focusable: Boolean = false,
    content: @Composable () -> Unit,
) {
    FlyOverlayLayer(touchable = true, focusable = focusable, onBackPress = onDismissRequest) {
        val density = LocalDensity.current
        val margin = with(density) { 8.dp.roundToPx() }
        val offsetPx = with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
        val noRipple = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = noRipple, indication = null, onClick = onDismissRequest)
        ) {
            AnchoredContent(anchor, side, alignment, offsetPx, gap = 0, margin = margin) {
                // a touch on the content does not reach the layer behind it
                Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                    content()
                }
            }
        }
    }
}

/** Colors and shape of what is drawn by [FlyPopup] and its relatives, following the system dark theme */
object FlyPopupDefaults {
    val Shape: Shape = RoundedCornerShape(8.dp)

    @Composable
    fun containerColor(): Color = if (isSystemInDarkTheme()) Color(0xFF2B2930) else Color(0xFFF3EDF7)

    @Composable
    fun contentColor(): Color = if (isSystemInDarkTheme()) Color(0xFFE6E0E9) else Color(0xFF1D1B20)
}

/** A rounded surface with a shadow: the container of menus and toolbars */
@Composable
internal fun FlyPopupSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = FlyPopupDefaults.containerColor(),
    shape: Shape = FlyPopupDefaults.Shape,
    content: @Composable () -> Unit,
) {
    Box(modifier.shadow(6.dp, shape).background(containerColor, shape)) { content() }
}

/** [FlyPopup] next to a [FlyAnchor], its position is read now */
@Composable
fun FlyPopup(
    anchor: FlyAnchor,
    onDismissRequest: () -> Unit,
    side: FlyPopupSide = PopupSide.Bottom,
    alignment: FlyPopupAlignment = PopupAlignment.Start,
    offset: DpOffset = DpOffset.Zero,
    focusable: Boolean = false,
    content: @Composable () -> Unit,
) = FlyPopup(anchor.bounds, onDismissRequest, side, alignment, offset, focusable, content)

/** [FlyDropdownMenu] next to a [FlyAnchor], its position is read now */
@Composable
fun FlyDropdownMenu(
    anchor: FlyAnchor,
    onDismissRequest: () -> Unit,
    side: FlyPopupSide = PopupSide.Bottom,
    alignment: FlyPopupAlignment = PopupAlignment.Start,
    offset: DpOffset = DpOffset.Zero,
    focusable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) = FlyDropdownMenu(anchor.bounds, onDismissRequest, side, alignment, offset, focusable, content)

/**
 * A menu next to [anchor] that works inside a [FlyWindow], where `DropdownMenu` needs an activity window.
 * Compose it while it is open. It is a [FlyPopup]: a touch outside closes it, it flips when there is no room.
 * Close it yourself when an item is clicked.
 *
 * ```
 * var open by remember { mutableStateOf(false) }
 * val anchor = rememberFlyAnchor()
 * Button(onClick = { open = true }, modifier = Modifier.flyAnchor(anchor)) { Text("More") }
 * if (open) FlyDropdownMenu(anchor, onDismissRequest = { open = false }) {
 *     FlyDropdownMenuItem("Share", onClick = { share(); open = false })
 * }
 * ```
 */
@Composable
fun FlyDropdownMenu(
    anchor: Rect,
    onDismissRequest: () -> Unit,
    side: FlyPopupSide = PopupSide.Bottom,
    alignment: FlyPopupAlignment = PopupAlignment.Start,
    offset: DpOffset = DpOffset.Zero,
    focusable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    FlyPopup(anchor, onDismissRequest, side, alignment, offset, focusable) {
        FlyPopupSurface(Modifier.width(IntrinsicSize.Max).widthIn(min = 112.dp, max = 280.dp)) {
            Column(Modifier.padding(vertical = 8.dp)) { content() }
        }
    }
}

/** An item of a [FlyDropdownMenu]. It does not close the menu. */
@Composable
fun FlyDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    contentColor: Color = FlyPopupDefaults.contentColor(),
) {
    Row(
        modifier
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp)
            .alpha(if (enabled) 1f else .38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(Modifier.padding(end = 12.dp)) { leadingIcon() }
        }
        BasicText(text, style = TextStyle(color = contentColor, fontSize = 16.sp, lineHeight = 24.sp))
    }
}
