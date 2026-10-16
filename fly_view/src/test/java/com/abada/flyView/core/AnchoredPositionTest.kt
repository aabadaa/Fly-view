package com.abada.flyView.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnchoredPositionTest {
    private val frame = FlyRect(0, 100, 1000, 2000)

    private fun position(h: HAnchor, v: VAnchor) = AnchoredPosition(h, v, frame, w = 200, height = 100)

    @Test
    fun noGravityMeansOffsetsFromTheCenter() {
        val p = position(HAnchor.Center, VAnchor.Center)
        assertEquals(400, p.absX(0))
        assertEquals(1000, p.absY(0)) // 100 + (1900 - 100) / 2
        assertEquals(500, p.absX(100))
        assertEquals(0, p.paramX(400))
    }

    @Test
    fun startTopMeansOffsetsFromTheTopLeft() {
        val p = position(HAnchor.Start, VAnchor.Top)
        assertEquals(50, p.absX(50))
        assertEquals(150, p.absY(50))
        assertEquals(50, p.paramX(50))
        assertEquals(50, p.paramY(150))
        assertFalse(p.flipX)
        assertFalse(p.flipY)
    }

    @Test
    fun endBottomMeansOffsetsGrowInwards() {
        val p = position(HAnchor.End, VAnchor.Bottom)
        assertEquals(750, p.absX(50)) // 1000 - 200 - 50
        assertEquals(1850, p.absY(50)) // 2000 - 100 - 50
        assertTrue(p.flipX)
        assertTrue(p.flipY)
    }

    @Test
    fun paramAndAbsAreInverses() {
        for (h in HAnchor.entries) for (v in VAnchor.entries) {
            val p = position(h, v)
            for (x in listOf(-300, 0, 77, 500)) assertEquals(x, p.paramX(p.absX(x)), "x $h")
            for (y in listOf(-300, 0, 77, 500)) assertEquals(y, p.paramY(p.absY(y)), "y $v")
        }
    }
}
