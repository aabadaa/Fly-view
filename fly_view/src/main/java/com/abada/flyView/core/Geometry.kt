package com.abada.flyView.core

/** Pure geometry used by the core. The core has no Android dependency so it runs in plain JVM tests. */
data class FlyRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class FlyPoint(val x: Int, val y: Int)

data class FlySizePx(val width: Int, val height: Int) {
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

/** Pixels per second */
data class FlyVelocity(val x: Float = 0f, val y: Float = 0f)

enum class EdgeSide { Left, Right }

internal fun Int.coerceInRange(min: Int, max: Int): Int = coerceIn(min, maxOf(min, max))
