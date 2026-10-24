package com.abada.flyView.core

/** Which side of the anchor a popup goes. `Left` and `Right` are absolute, they do not follow the layout direction. */
enum class PopupSide { Top, Bottom, Left, Right }

/** Where a popup lines up along the edge of the anchor it is next to: the start (left/top), the center, or the end */
enum class PopupAlignment { Start, Center, End }

/** Where a popup goes: its top-left corner, and the side of the anchor it ended up on (it may differ from the preferred one) */
data class PopupPlacement(val position: FlyPoint, val side: PopupSide)

/**
 * Places a popup of [size] next to [anchor], inside [bounds] (all in the same coordinates). It is the one
 * placement used by tooltips, popups, menus and the text toolbar.
 *
 * - The popup goes on [side] of the anchor, lined up as [alignment] says, [gap] away from it.
 * - When there is no room on that side it flips to the opposite one. When neither side has room the one
 *   with more space wins (the preferred side on a tie).
 * - It is then clamped inside [bounds], keeping [margin] to the edges. A popup bigger than the room sticks
 *   to the start/top edge.
 * - The room ends at [keyboardTop] when the keyboard covers the bottom of [bounds].
 *
 * [offsetX] and [offsetY] move the popup after the side is chosen; they are not mirrored when it flips.
 */
fun placePopup(
    anchor: FlyRect,
    size: FlySizePx,
    bounds: FlyRect,
    side: PopupSide = PopupSide.Bottom,
    alignment: PopupAlignment = PopupAlignment.Start,
    offsetX: Int = 0,
    offsetY: Int = 0,
    gap: Int = 0,
    margin: Int = 0,
    keyboardTop: Int? = null,
): PopupPlacement {
    val room = if (keyboardTop != null && keyboardTop > bounds.top) {
        bounds.copy(bottom = minOf(bounds.bottom, keyboardTop))
    } else bounds
    val minX = room.left + margin
    val minY = room.top + margin
    val maxX = room.right - size.width - margin
    val maxY = room.bottom - size.height - margin

    fun alongX() = when (alignment) {
        PopupAlignment.Start -> anchor.left
        PopupAlignment.Center -> (anchor.centerX - size.width / 2f).toInt()
        PopupAlignment.End -> anchor.right - size.width
    }

    fun alongY() = when (alignment) {
        PopupAlignment.Start -> anchor.top
        PopupAlignment.Center -> (anchor.centerY - size.height / 2f).toInt()
        PopupAlignment.End -> anchor.bottom - size.height
    }

    fun origin(s: PopupSide): FlyPoint = when (s) {
        PopupSide.Top -> FlyPoint(alongX(), anchor.top - gap - size.height)
        PopupSide.Bottom -> FlyPoint(alongX(), anchor.bottom + gap)
        PopupSide.Left -> FlyPoint(anchor.left - gap - size.width, alongY())
        PopupSide.Right -> FlyPoint(anchor.right + gap, alongY())
    }.let { FlyPoint(it.x + offsetX, it.y + offsetY) }

    fun fits(s: PopupSide): Boolean {
        val p = origin(s)
        return when (s) {
            PopupSide.Top, PopupSide.Bottom -> p.y in minY..maxY
            PopupSide.Left, PopupSide.Right -> p.x in minX..maxX
        }
    }

    fun room(s: PopupSide): Int = when (s) {
        PopupSide.Top -> anchor.top - room.top
        PopupSide.Bottom -> room.bottom - anchor.bottom
        PopupSide.Left -> anchor.left - room.left
        PopupSide.Right -> room.right - anchor.right
    }

    val opposite = when (side) {
        PopupSide.Top -> PopupSide.Bottom
        PopupSide.Bottom -> PopupSide.Top
        PopupSide.Left -> PopupSide.Right
        PopupSide.Right -> PopupSide.Left
    }
    val chosen = when {
        fits(side) -> side
        fits(opposite) -> opposite
        room(side) >= room(opposite) -> side
        else -> opposite
    }
    val p = origin(chosen)
    return PopupPlacement(FlyPoint(p.x.coerceInRange(minX, maxX), p.y.coerceInRange(minY, maxY)), chosen)
}
