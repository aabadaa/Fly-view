package com.abada.flyView.core

enum class DockEdge { Top, Bottom, Start, End }

/** How big a docked window is along its edge */
sealed interface SizeSpec {
    data object Wrap : SizeSpec
    data object Fill : SizeSpec
    data class Fixed(val px: Int) : SizeSpec
    data class Fraction(val value: Float) : SizeSpec
}

sealed interface PlacementSpec {
    /** Free window, its position comes from the window state */
    data object Floating : PlacementSpec

    /** Stuck to an edge. [margin] is the gap to the edge, [respectInsets] keeps it out of the system bars */
    data class Docked(
        val edge: DockEdge,
        val size: SizeSpec = SizeSpec.Wrap,
        val margin: Int = 0,
        val respectInsets: Boolean = true,
    ) : PlacementSpec

    data object Fullscreen : PlacementSpec
}

sealed interface Dim {
    data object Wrap : Dim
    data object Match : Dim
    data class Px(val value: Int) : Dim
}

/** What the window manager should be told: anchors (gravity), size and offsets */
data class FrameSpec(
    val h: HAnchor,
    val v: VAnchor,
    val width: Dim,
    val height: Dim,
    val x: Int,
    val y: Int,
    /** `true` when the window covers the system bars area too */
    val ignoreInsets: Boolean,
)

object PlacementResolver {
    /**
     * @param floating the current floating offsets (window manager `x`, `y`), used for [PlacementSpec.Floating]
     * @param floatingH the anchors the floating window uses
     * @param usable the display without system bars and cutout
     */
    fun resolve(
        placement: PlacementSpec,
        floating: FlyPoint,
        floatingH: HAnchor,
        floatingV: VAnchor,
        usable: FlyRect,
    ): FrameSpec = when (placement) {
        PlacementSpec.Floating -> FrameSpec(
            floatingH, floatingV, Dim.Wrap, Dim.Wrap, floating.x, floating.y, ignoreInsets = false
        )

        PlacementSpec.Fullscreen -> FrameSpec(
            HAnchor.Start, VAnchor.Top, Dim.Match, Dim.Match, 0, 0, ignoreInsets = true
        )

        is PlacementSpec.Docked -> {
            val horizontalEdge = placement.edge == DockEdge.Top || placement.edge == DockEdge.Bottom
            val axisLength = if (horizontalEdge) usable.width else usable.height
            val along = when (val s = placement.size) {
                SizeSpec.Wrap -> Dim.Wrap
                SizeSpec.Fill -> Dim.Match
                is SizeSpec.Fixed -> Dim.Px(s.px)
                is SizeSpec.Fraction -> Dim.Px((axisLength * s.value.coerceIn(0f, 1f)).toInt())
            }
            val h = when (placement.edge) {
                DockEdge.Start -> HAnchor.Start
                DockEdge.End -> HAnchor.End
                else -> HAnchor.Center
            }
            val v = when (placement.edge) {
                DockEdge.Top -> VAnchor.Top
                DockEdge.Bottom -> VAnchor.Bottom
                else -> VAnchor.Center
            }
            FrameSpec(
                h = h,
                v = v,
                width = if (horizontalEdge) along else Dim.Wrap,
                height = if (horizontalEdge) Dim.Wrap else along,
                x = if (horizontalEdge) 0 else placement.margin,
                y = if (horizontalEdge) placement.margin else 0,
                ignoreInsets = !placement.respectInsets,
            )
        }
    }
}
