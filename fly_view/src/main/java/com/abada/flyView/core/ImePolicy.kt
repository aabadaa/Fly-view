package com.abada.flyView.core

/** The keyboard rules of a floating window, in absolute screen pixels */
object ImePolicy {
    /** A window only gets the part of the keyboard it overlaps: the top is its bottom minus that overlap */
    fun keyboardTop(windowBottom: Int, overlap: Int): Int = windowBottom - overlap

    /**
     * How far up the window has to be lifted to stay above the keyboard. 0 when there is no keyboard
     * or the window is already clear of it, and never more than the window can go up on the screen.
     */
    fun shift(windowTop: Int, windowHeight: Int, keyboardTop: Int?): Int {
        if (keyboardTop == null) return 0
        return (windowTop + windowHeight - keyboardTop).coerceIn(0, maxOf(0, windowTop))
    }

    /** The lowest top of the window while it is dragged: the bottom stays above the keyboard */
    fun limitTop(top: Int, windowHeight: Int, keyboardTop: Int?): Int =
        if (keyboardTop == null) top else minOf(top, keyboardTop - windowHeight)
}
