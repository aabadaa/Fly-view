package com.abada.flyView.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlacementTest {
    private val usable = FlyRect(0, 100, 1000, 2000)

    private fun resolve(p: PlacementSpec) =
        PlacementResolver.resolve(p, FlyPoint(7, 9), HAnchor.Center, VAnchor.Center, usable)

    @Test
    fun floatingKeepsThePositionAndIsMovable() {
        val f = resolve(PlacementSpec.Floating)
        assertEquals(FrameSpec(HAnchor.Center, VAnchor.Center, Dim.Wrap, Dim.Wrap, 7, 9, true, false), f)
    }

    @Test
    fun theKeyboardBarIsABottomDockFillingTheWidth() {
        val f = resolve(PlacementSpec.Docked(DockEdge.Bottom, SizeSpec.Fill))
        assertEquals(VAnchor.Bottom, f.v)
        assertEquals(Dim.Match, f.width)
        assertEquals(Dim.Wrap, f.height)
        assertEquals(0, f.x)
        assertEquals(0, f.y)
        assertFalse(f.movable)
        assertFalse(f.ignoreInsets)
    }

    @Test
    fun marginIsTheGapToTheEdge() {
        val f = resolve(PlacementSpec.Docked(DockEdge.Top, SizeSpec.Wrap, margin = 24))
        assertEquals(VAnchor.Top, f.v)
        assertEquals(24, f.y)
        val side = resolve(PlacementSpec.Docked(DockEdge.End, SizeSpec.Wrap, margin = 24))
        assertEquals(HAnchor.End, side.h)
        assertEquals(24, side.x)
    }

    @Test
    fun sizeIsAlongTheDockedEdge() {
        val bottom = resolve(PlacementSpec.Docked(DockEdge.Bottom, SizeSpec.Fraction(0.5f)))
        assertEquals(Dim.Px(500), bottom.width)
        val start = resolve(PlacementSpec.Docked(DockEdge.Start, SizeSpec.Fixed(300)))
        assertEquals(Dim.Px(300), start.height)
        assertEquals(Dim.Wrap, start.width)
        val fraction = resolve(PlacementSpec.Docked(DockEdge.Start, SizeSpec.Fraction(0.5f)))
        assertEquals(Dim.Px(950), fraction.height) // half of the usable height
    }

    @Test
    fun fullscreenCoversEverythingAndIgnoresInsets() {
        val f = resolve(PlacementSpec.Fullscreen)
        assertEquals(Dim.Match, f.width)
        assertEquals(Dim.Match, f.height)
        assertTrue(f.ignoreInsets)
        assertFalse(f.movable)
    }

    @Test
    fun dockedCanDrawBehindTheSystemBars() {
        val f = resolve(PlacementSpec.Docked(DockEdge.Bottom, respectInsets = false))
        assertTrue(f.ignoreInsets)
    }

    @Test
    fun startPositionsResolveInsideTheBounds() {
        val size = FlySizePx(200, 100)
        assertEquals(FlyPoint(400, 1000), PositionResolver.resolve(PositionSpec.Center, size, usable))
        assertEquals(
            FlyPoint(800, 100 + (1900 - 100) * 3 / 10),
            PositionResolver.resolve(PositionSpec.Edge(EdgeSide.Right, 0.3f), size, usable)
        )
        assertEquals(FlyPoint(0, 100), PositionResolver.resolve(PositionSpec.Absolute(-5, -5), size, usable))
    }
}
