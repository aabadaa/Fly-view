package com.abada.flyView.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** The keyboard cases of a floating window, as numbers: no emulator needed */
class ImePolicyTest {
    // a 2000 px high screen, the keyboard covers the bottom 800 px: its top is at 1200
    private val keyboardTop = 1200

    @Test
    fun theKeyboardTopComesFromTheOverlapNotFromTheOverlapAsHeight() {
        // a window that ends at 1500 and is covered by 300 px: the keyboard starts at 1200, not at 1700
        assertEquals(1200, ImePolicy.keyboardTop(windowBottom = 1500, overlap = 300))
    }

    @Test
    fun noKeyboardNoShift() {
        assertEquals(0, ImePolicy.shift(windowTop = 1500, windowHeight = 200, keyboardTop = null))
    }

    @Test
    fun aWindowAboveTheKeyboardStaysWhereItIs() {
        assertEquals(0, ImePolicy.shift(windowTop = 400, windowHeight = 200, keyboardTop = keyboardTop))
    }

    @Test
    fun aWindowUnderTheKeyboardIsLiftedByTheHiddenPart() {
        // the window spans 1100..1300: 100 px are under the keyboard
        assertEquals(100, ImePolicy.shift(windowTop = 1100, windowHeight = 200, keyboardTop = keyboardTop))
        // the window is completely behind it
        assertEquals(500, ImePolicy.shift(windowTop = 1500, windowHeight = 200, keyboardTop = keyboardTop))
    }

    @Test
    fun aTallWindowIsLiftedByItsWholeHiddenPart() {
        // the overlap alone (what the window's own inset reports) would give the wrong shift here
        val top = 700
        val height = 1100 // ends at 1800, 600 px under the keyboard
        assertEquals(600, ImePolicy.shift(top, height, keyboardTop))
    }

    @Test
    fun theShiftNeverPushesTheWindowAboveTheScreen() {
        // the window is taller than the free space: it goes up to the top and no further
        assertEquals(100, ImePolicy.shift(windowTop = 100, windowHeight = 1500, keyboardTop = keyboardTop))
    }

    @Test
    fun resizingWhileTheKeyboardIsOpenChangesTheShift() {
        assertEquals(0, ImePolicy.shift(windowTop = 900, windowHeight = 200, keyboardTop = keyboardTop))
        // the content expands: the window grows downward under the keyboard
        assertEquals(200, ImePolicy.shift(windowTop = 900, windowHeight = 500, keyboardTop = keyboardTop))
        // and collapses again
        assertEquals(0, ImePolicy.shift(windowTop = 900, windowHeight = 200, keyboardTop = keyboardTop))
    }

    @Test
    fun draggingStopsAboveTheKeyboard() {
        assertEquals(1000, ImePolicy.limitTop(top = 1500, windowHeight = 200, keyboardTop = keyboardTop))
        assertEquals(800, ImePolicy.limitTop(top = 800, windowHeight = 200, keyboardTop = keyboardTop))
        assertEquals(1500, ImePolicy.limitTop(top = 1500, windowHeight = 200, keyboardTop = null))
    }
}
