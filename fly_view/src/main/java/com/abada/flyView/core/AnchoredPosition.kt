package com.abada.flyView.core

enum class HAnchor { Start, End, Center }
enum class VAnchor { Top, Bottom, Center }

/**
 * Converts between window manager offsets (`LayoutParams.x/y`) and absolute top-left positions.
 *
 * What `x` and `y` mean depends on the window gravity: an offset from the start/top edge,
 * from the end/bottom edge (growing inwards), or from the center (no gravity, the default).
 * [h] and [v] are the gravity with the layout direction already resolved (Start = left).
 */
class AnchoredPosition(
    private val h: HAnchor,
    private val v: VAnchor,
    private val frame: FlyRect,
    private val w: Int,
    private val height: Int,
) {
    /** `true` if a growing `x` moves the window towards the start */
    val flipX: Boolean get() = h == HAnchor.End

    /** `true` if a growing `y` moves the window up */
    val flipY: Boolean get() = v == VAnchor.Bottom

    fun absX(x: Int): Int = when (h) {
        HAnchor.Start -> frame.left + x
        HAnchor.End -> frame.right - w - x
        HAnchor.Center -> frame.left + (frame.width - w) / 2 + x
    }

    fun absY(y: Int): Int = when (v) {
        VAnchor.Top -> frame.top + y
        VAnchor.Bottom -> frame.bottom - height - y
        VAnchor.Center -> frame.top + (frame.height - height) / 2 + y
    }

    fun paramX(absX: Int): Int = when (h) {
        HAnchor.Start -> absX - frame.left
        HAnchor.End -> frame.right - w - absX
        HAnchor.Center -> absX - frame.left - (frame.width - w) / 2
    }

    fun paramY(absY: Int): Int = when (v) {
        VAnchor.Top -> absY - frame.top
        VAnchor.Bottom -> frame.bottom - height - absY
        VAnchor.Center -> absY - frame.top - (frame.height - height) / 2
    }
}
