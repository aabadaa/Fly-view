package com.abada.flyView.core

/** Where a window starts, before the user ever moved it. */
sealed interface PositionSpec {
    data object Center : PositionSpec
    data class Absolute(val x: Int, val y: Int) : PositionSpec

    /** Flush to [side], at [y] (0 = top, 1 = bottom of the free height) */
    data class Edge(val side: EdgeSide, val y: Float = 0.5f, val padding: Int = 0) : PositionSpec
}

object PositionResolver {
    fun resolve(spec: PositionSpec, size: FlySizePx, bounds: FlyRect): FlyPoint = when (spec) {
        PositionSpec.Center -> SnapPolicy.clamp(
            FlyPoint(
                bounds.left + (bounds.width - size.width) / 2,
                bounds.top + (bounds.height - size.height) / 2
            ), size, bounds
        )

        is PositionSpec.Absolute -> SnapPolicy.clamp(FlyPoint(spec.x, spec.y), size, bounds)
        is PositionSpec.Edge -> {
            val x = if (spec.side == EdgeSide.Right) bounds.right - size.width - spec.padding
            else bounds.left + spec.padding
            val y = bounds.top + ((bounds.height - size.height) * spec.y.coerceIn(0f, 1f)).toInt()
            SnapPolicy.clamp(FlyPoint(x, y), size, bounds)
        }
    }
}
