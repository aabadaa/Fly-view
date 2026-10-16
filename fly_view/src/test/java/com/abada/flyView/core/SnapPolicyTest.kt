package com.abada.flyView.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapPolicyTest {
    private val bounds = FlyRect(0, 100, 1000, 2000)
    private val size = FlySizePx(200, 100)

    @Test
    fun clampKeepsTheWindowInside() {
        assertEquals(FlyPoint(800, 1900), SnapPolicy.clamp(FlyPoint(5000, 5000), size, bounds))
        assertEquals(FlyPoint(0, 100), SnapPolicy.clamp(FlyPoint(-50, -50), size, bounds))
        assertEquals(FlyPoint(300, 400), SnapPolicy.clamp(FlyPoint(300, 400), size, bounds))
    }

    @Test
    fun aWindowBiggerThanTheBoundsStaysAtTheStart() {
        val big = FlySizePx(2000, 3000)
        assertEquals(FlyPoint(0, 100), SnapPolicy.clamp(FlyPoint(10, 10), big, bounds))
    }

    @Test
    fun settleSnapsToTheClosestEdge() {
        val left = SnapPolicy.settle(FlyPoint(100, 500), size, bounds, snapToEdges = 0)
        assertEquals(Settled(FlyPoint(0, 500), EdgeSide.Left), left)
        val right = SnapPolicy.settle(FlyPoint(600, 500), size, bounds, snapToEdges = 0)
        assertEquals(Settled(FlyPoint(800, 500), EdgeSide.Right), right)
    }

    @Test
    fun settleUsesTheEdgePadding() {
        val right = SnapPolicy.settle(FlyPoint(600, 500), size, bounds, snapToEdges = 16)
        assertEquals(FlyPoint(784, 500), right.position)
        val left = SnapPolicy.settle(FlyPoint(0, 500), size, bounds, snapToEdges = 16)
        assertEquals(FlyPoint(16, 500), left.position)
    }

    @Test
    fun aFlingPicksTheEdgeItIsHeadingTo() {
        // center is on the left half but a fast fling to the right wins
        val settled = SnapPolicy.settle(FlyPoint(300, 500), size, bounds, FlyVelocity(x = 3000f), snapToEdges = 0)
        assertEquals(EdgeSide.Right, settled.side)
        val slow = SnapPolicy.settle(FlyPoint(300, 500), size, bounds, FlyVelocity(x = 100f), snapToEdges = 0)
        assertEquals(EdgeSide.Left, slow.side)
    }

    @Test
    fun aVerticalFlingMovesTheRestingY() {
        val settled = SnapPolicy.settle(FlyPoint(0, 500), size, bounds, FlyVelocity(y = 1000f), snapToEdges = 0)
        assertEquals(620, settled.position.y)
    }

    @Test
    fun settleWithoutSnapOnlyClamps() {
        val settled = SnapPolicy.settle(FlyPoint(5000, 500), size, bounds)
        assertEquals(FlyPoint(800, 500), settled.position)
        assertNull(settled.side)
    }

    @Test
    fun reanchorKeepsAWindowFlushWhenItsSizeChanges() {
        val grown = FlySizePx(400, 100)
        val onRight = SnapPolicy.reanchor(FlyPoint(800, 500), grown, bounds, EdgeSide.Right, 0)
        assertEquals(FlyPoint(600, 500), onRight)
        val onLeft = SnapPolicy.reanchor(FlyPoint(5, 500), grown, bounds, EdgeSide.Left, 10)
        assertEquals(FlyPoint(10, 500), onLeft)
        val free = SnapPolicy.reanchor(FlyPoint(900, 500), grown, bounds, null, 0)
        assertEquals(FlyPoint(600, 500), free)
    }

    @Test
    fun dismissZoneUsesTheCenterOfTheWindow() {
        val display = FlyRect(0, 0, 1000, 2000)
        // zone starts at 2000 - 2000 * 0.18 = 1640
        assertTrue(SnapPolicy.isOverDismissZone(FlyPoint(0, 1600), size, display, 0.18f)) // center 1650
        assertFalse(SnapPolicy.isOverDismissZone(FlyPoint(0, 1500), size, display, 0.18f)) // center 1550
    }
}
