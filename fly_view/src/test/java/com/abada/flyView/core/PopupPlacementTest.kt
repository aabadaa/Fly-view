package com.abada.flyView.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PopupPlacementTest {
    private val bounds = FlyRect(0, 0, 1000, 2000)
    private val size = FlySizePx(200, 80)

    private fun place(
        anchor: FlyRect,
        side: PopupSide = PopupSide.Top,
        alignment: PopupAlignment = PopupAlignment.Center,
        bounds: FlyRect = this.bounds,
        size: FlySizePx = this.size,
        offsetX: Int = 0,
        offsetY: Int = 0,
        keyboardTop: Int? = null,
    ) = placePopup(anchor, size, bounds, side, alignment, offsetX, offsetY, gap = 10, margin = 20, keyboardTop = keyboardTop)

    @Test
    fun isCenteredAboveTheAnchorWhenThereIsRoom() {
        val p = place(FlyRect(400, 500, 600, 600))
        assertEquals(PopupSide.Top, p.side)
        assertEquals(FlyPoint(400, 410), p.position) // 500 - 10 - 80
    }

    @Test
    fun flipsBelowWhenTheTopHasNoRoom() {
        val p = place(FlyRect(400, 60, 600, 160)) // above would start at -30
        assertEquals(PopupSide.Bottom, p.side)
        assertEquals(FlyPoint(400, 170), p.position)
    }

    @Test
    fun keepsTheMarginAtTheTopBeforeFlipping() {
        assertEquals(PopupSide.Bottom, place(FlyRect(400, 105, 600, 205)).side) // would start at 15
        assertEquals(PopupSide.Top, place(FlyRect(400, 110, 600, 210)).side) // starts exactly at the margin
    }

    @Test
    fun aBottomPopupFlipsAboveAtTheBottomOfTheScreen() {
        val p = place(FlyRect(400, 1900, 600, 1980), side = PopupSide.Bottom, alignment = PopupAlignment.Start)
        assertEquals(PopupSide.Top, p.side)
        assertEquals(FlyPoint(400, 1810), p.position)
    }

    @Test
    fun alignmentLinesUpWithTheEdgesOfTheAnchor() {
        val anchor = FlyRect(400, 500, 700, 600)
        val below = PopupSide.Bottom
        assertEquals(400, place(anchor, below, PopupAlignment.Start).position.x)
        assertEquals(450, place(anchor, below, PopupAlignment.Center).position.x)
        assertEquals(500, place(anchor, below, PopupAlignment.End).position.x) // 700 - 200
    }

    @Test
    fun sidePopupsAlignAlongTheVerticalEdge() {
        val anchor = FlyRect(400, 500, 600, 700)
        val right = place(anchor, PopupSide.Right, PopupAlignment.Start)
        assertEquals(FlyPoint(610, 500), right.position)
        assertEquals(PopupSide.Right, right.side)
        val left = place(anchor, PopupSide.Left, PopupAlignment.End)
        assertEquals(FlyPoint(190, 620), left.position) // 400 - 10 - 200, 700 - 80
    }

    @Test
    fun aSidePopupFlipsWhenItHitsTheScreenEdge() {
        val p = place(FlyRect(850, 500, 950, 600), PopupSide.Right, PopupAlignment.Center)
        assertEquals(PopupSide.Left, p.side)
        assertEquals(640, p.position.x) // 850 - 10 - 200
    }

    @Test
    fun isClampedToTheLeftAndRightMargins() {
        assertEquals(20, place(FlyRect(0, 500, 40, 600)).position.x)
        assertEquals(780, place(FlyRect(960, 500, 1000, 600)).position.x) // 1000 - 200 - 20
    }

    @Test
    fun stickToTheStartWhenWiderThanTheBounds() {
        val p = place(FlyRect(100, 500, 200, 600), size = FlySizePx(1200, 80))
        assertEquals(20, p.position.x)
    }

    @Test
    fun picksTheSideWithMoreRoomWhenNeitherFits() {
        val short = FlyRect(0, 0, 1000, 300)
        val tall = FlySizePx(200, 250)
        val above = place(FlyRect(400, 180, 600, 220), bounds = short, size = tall)
        assertEquals(PopupSide.Top, above.side)
        assertEquals(20, above.position.y) // clamped to the top margin
        val below = place(FlyRect(400, 40, 600, 80), bounds = short, size = tall)
        assertEquals(PopupSide.Bottom, below.side)
        assertEquals(30, below.position.y) // 300 - 250 - 20
    }

    @Test
    fun theKeyboardShortensTheRoomBelow() {
        val anchor = FlyRect(400, 1000, 600, 1100)
        assertEquals(PopupSide.Bottom, place(anchor, PopupSide.Bottom, PopupAlignment.Center).side)
        // the keyboard starts at 1150: 80 px do not fit below (1110 + 80 > 1130), so it goes above
        val p = place(anchor, PopupSide.Bottom, PopupAlignment.Center, keyboardTop = 1150)
        assertEquals(PopupSide.Top, p.side)
        assertEquals(910, p.position.y)
    }

    @Test
    fun aKeyboardAboveTheBoundsIsIgnored() {
        val p = place(FlyRect(400, 500, 600, 600), keyboardTop = -5)
        assertEquals(PopupSide.Top, p.side)
    }

    @Test
    fun offsetsMoveThePopupAfterTheSideIsChosen() {
        val p = place(FlyRect(400, 500, 600, 600), PopupSide.Bottom, PopupAlignment.Start, offsetX = 12, offsetY = 4)
        assertEquals(FlyPoint(412, 614), p.position) // 600 + 10 + 4
    }

    @Test
    fun boundsThatDoNotStartAtZeroAreRespected() {
        val shifted = FlyRect(100, 200, 1100, 2200)
        val p = place(FlyRect(600, 250, 700, 300), bounds = shifted)
        assertEquals(PopupSide.Bottom, p.side)
        assertEquals(FlyPoint(550, 310), p.position)
        assertEquals(120, place(FlyRect(100, 900, 120, 920), bounds = shifted).position.x)
    }

    @Test
    fun anAnchorOutsideTheBoundsStillGivesAPlaceInside() {
        val p = place(FlyRect(-500, -500, -400, -400))
        assertEquals(20, p.position.x)
        assertEquals(true, p.position.y in 20..(2000 - 80 - 20))
    }
}
