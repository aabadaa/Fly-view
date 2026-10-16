package com.abada.flyView.core

import kotlin.math.roundToInt

/** Where a window should rest after a drag, and the edge it is snapped to (if any). */
data class Settled(val position: FlyPoint, val side: EdgeSide?)

/** Pure drag, snap and dismiss math. All positions are absolute top-left pixels. */
object SnapPolicy {
    /** How far (in seconds) the fling velocity is projected to pick the edge to snap to */
    const val FLING_PROJECTION_SECONDS = 0.12f

    /** Keeps a window of [size] inside [bounds] (it stays at the start/top if it is bigger) */
    fun clamp(position: FlyPoint, size: FlySizePx, bounds: FlyRect): FlyPoint = FlyPoint(
        position.x.coerceInRange(bounds.left, bounds.right - size.width),
        position.y.coerceInRange(bounds.top, bounds.bottom - size.height),
    )

    /**
     * Where the window rests after a drag ends.
     *
     * @param snapToEdges `null` to only keep the window inside [bounds], or the gap to the edge
     * (in pixels) to fly to the closest horizontal edge, the fling [velocity] is taken in account
     */
    fun settle(
        position: FlyPoint,
        size: FlySizePx,
        bounds: FlyRect,
        velocity: FlyVelocity = FlyVelocity(),
        snapToEdges: Int? = null,
    ): Settled {
        val y = (position.y + velocity.y * FLING_PROJECTION_SECONDS).roundToInt()
            .coerceInRange(bounds.top, bounds.bottom - size.height)
        if (snapToEdges == null) {
            return Settled(FlyPoint(position.x.coerceInRange(bounds.left, bounds.right - size.width), y), null)
        }
        val projectedCenter = position.x + size.width / 2f + velocity.x * FLING_PROJECTION_SECONDS
        val toRight = projectedCenter > bounds.centerX
        val x = if (toRight) (bounds.right - size.width - snapToEdges).coerceAtLeast(bounds.left)
        else (bounds.left + snapToEdges).coerceAtMost(maxOf(bounds.left, bounds.right - size.width))
        return Settled(FlyPoint(x, y), if (toRight) EdgeSide.Right else EdgeSide.Left)
    }

    /**
     * Keeps a window on its [side] and inside [bounds] after its size or the screen changed.
     * With no side it only clamps.
     */
    fun reanchor(position: FlyPoint, size: FlySizePx, bounds: FlyRect, side: EdgeSide?, edgePadding: Int): FlyPoint {
        val x = when (side) {
            EdgeSide.Left -> bounds.left + edgePadding
            EdgeSide.Right -> bounds.right - size.width - edgePadding
            null -> position.x
        }
        return clamp(FlyPoint(x, position.y), size, bounds)
    }

    /** `true` if the center of a window at [position] is inside the dismiss zone at the bottom of [display] */
    fun isOverDismissZone(position: FlyPoint, size: FlySizePx, display: FlyRect, zoneHeightFraction: Float): Boolean {
        val centerY = position.y + size.height / 2f
        return centerY >= display.bottom - display.height * zoneHeightFraction
    }
}
