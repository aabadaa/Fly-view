package com.abada.flyView

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abada.flyView.windowManagerUtils.DismissOverlay
import com.abada.flyView.windowManagerUtils.WindowPosition
import com.abada.flyView.windowManagerUtils.displayBounds
import com.abada.flyView.windowManagerUtils.removeFlyView
import com.abada.flyView.windowManagerUtils.usableBounds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Describes the area at the bottom of the screen where a dragged [FlyViewInfo] is dismissed.
 *
 * While the user drags a view an overlay (see `closeViewContent`) is shown in this area,
 * and releasing the view while its center is inside the area removes it.
 *
 * @property heightFraction The height of the area as a fraction of the screen height
 * @property hapticFeedback Vibrate a little when the view enters the area
 */
data class DismissZone(
    val heightFraction: Float = 0.18f,
    val hapticFeedback: Boolean = true,
)

/**
 * The default [WindowManager.LayoutParams] of a [FlyViewInfo].
 *
 * `x` and `y` keep the window manager meaning, which depends on `gravity`: with no gravity
 * (the default) they are offsets from the center of the screen. Draggable views work with any
 * gravity.
 */
fun defaultFlyViewParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT
).also { it.windowAnimations = android.R.style.Animation }

internal enum class Side { None, Left, Right }

/**
 * A holder to all information needed for the [FlyView]
 *
 * @param T The type of controller that extends [FlyController]
 * @property controller Used to send data from anywhere to the [FlyView]
 * @property onRemove Pass a lambda which will be called before removing the [FlyView].
 * It is suspending so you can play an exit animation, the view is not touchable meanwhile
 * @property params A [WindowManager.LayoutParams] that passed when adding the [FlyView]
 * (see [defaultFlyViewParams])
 * @property keyDispatcher This will be passed to the [FlyView] to handle key events
 * @property dismissZone Where the user can drop the view to remove it, `null` to disable it
 * @property focusable `false` / `true` sets whether the window takes the focus, `null` (the default)
 * leaves the flags of [params] as they are, see [focusable]
 * @property content The content of the flyView
 * @property removeView Call it in your view to remove it, it is safe to call it more than once
 */
class FlyViewInfo<T : FlyController>(
    val controller: T,
    val onRemove: suspend FlyViewInfo<T>.() -> Unit = {},
    val params: WindowManager.LayoutParams = defaultFlyViewParams(),
    val keyDispatcher: ((KeyEvent?) -> Boolean?)? = null,
    val dismissZone: DismissZone? = DismissZone(),
    focusable: Boolean? = null,
    internal val content: @Composable FlyViewInfo<T>.() -> Unit,
) {
    /** The actual FlyView instance that will be displayed */
    internal lateinit var flyView: FlyView

    private lateinit var appContext: Context
    private lateinit var windowManager: WindowManager
    private lateinit var key: String
    private var onRemoved: () -> Unit = {}
    private var attached = false
    private var removing = false
    private var dragging = false
    private var animator: ValueAnimator? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val initialFocusable = focusable

    // set by DraggableFlyView
    internal var edgePaddingPx = 0
    internal var snapDuration = 300L
    internal var snappedSide = Side.None
    private var draggableCount = 0
    private var pendingSnap = false
    private var dragStartX = 0
    private var dragStartY = 0

    /**
     * Whether the window takes the focus. When it is `false` the app below keeps the back button
     * and the keyboard, which is what you want for a widget that only shows information.
     * It reads and writes the `FLAG_NOT_FOCUSABLE` flag of [params], so changing the flag
     * yourself and calling [updateLayoutParams] is the same.
     */
    var focusable: Boolean
        get() = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
        set(value) {
            applyFocusable(value)
            updateLayoutParams()
        }

    /** `true` while the user drags this view over its [dismissZone]. Compose reads it as state. */
    var isOverDismissZone by mutableStateOf(false)
        private set

    /** The part of the screen this view can be placed in (without system bars and cutouts) */
    val screenBounds: Rect get() = windowManager.usableBounds(appContext)

    /** Time to wait after [onRemove] before the window is removed, so exit animations can finish */
    var removeDelayMs: Long = 100

    /** Call it in your view to remove it, it is safe to call it more than once */
    val removeView: () -> Unit = { requestRemove() }

    /**
     * Internal method to add this FlyView to the WindowManager.
     * Sets up the view hierarchy, lifecycle management, and event handling.
     *
     * @param context Context used to create the view
     * @param key Unique identifier for this FlyView
     * @param windowManager The WindowManager to add the view to
     * @param onRemoved Called once the view is gone
     */
    internal fun addToWindowManager(
        context: Context,
        key: String,
        windowManager: WindowManager,
        onRemoved: () -> Unit = {},
    ) {
        this.appContext = context
        this.key = key
        this.windowManager = windowManager
        this.onRemoved = onRemoved
        initialFocusable?.let { applyFocusable(it) }
        flyView = FlyView(
            context = context,
            keyDispatcher = keyDispatcher,
            content = { content() }
        ).also {
            it.onConfigChanged = { reanchor() }
            it.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
                if (r - l != oldR - oldL || b - t != oldB - oldT) onWindowSizeChanged()
            }
        }
        windowManager.addView(flyView, params)
        attached = true
    }

    /** Releases everything, called by `WindowManager.removeFlyView` */
    internal fun dispose() {
        if (!attached) return
        attached = false
        animator?.cancel()
        scope.cancel()
        try {
            windowManager.removeView(flyView)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "dispose: the view was already removed")
        }
        flyView.dispose()
        onRemoved()
    }

    private fun requestRemove() {
        if (!attached || removing) return
        removing = true
        animator?.cancel()
        scope.launch {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            updateLayoutParams()
            try {
                onRemove(this@FlyViewInfo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "onRemove failed", e)
            }
            delay(removeDelayMs)
            windowManager.removeFlyView(key)
        }
    }

    private fun applyFocusable(focusable: Boolean) {
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
    }

    /**
     * Call this method to apply your modification to [params] object.
     * Updates the view's layout parameters in the WindowManager.
     */
    fun updateLayoutParams() {
        if (!attached) return
        try {
            windowManager.updateViewLayout(flyView, params)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "updateLayoutParams: trying to update a removed view")
        }
    }

    /**
     * This method moves the fly view smoothly to a specific position.
     * Any running animation is cancelled.
     *
     * @param x Target X coordinate (defaults to current X position)
     * @param y Target Y coordinate (defaults to current Y position)
     * @param duration Animation duration in milliseconds
     */
    fun animateTo(x: Int = params.x, y: Int = params.y, duration: Long = 300) {
        snappedSide = Side.None
        moveTo(x, y, duration)
    }

    /**
     * Call this method to move the fly view to the closest horizontal edge of the screen,
     * it also moves it back inside the screen if it is outside.
     *
     * @param duration Animation duration in milliseconds
     */
    fun goToScreenBorder(duration: Long = 300) = settle(autoSnap = true, duration = duration)

    private fun moveTo(x: Int, y: Int, duration: Long) {
        animator?.cancel()
        animator = null
        val startX = params.x
        val startY = params.y
        if (duration <= 0 || !attached || (startX == x && startY == y)) {
            setPosition(x, y)
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val f = it.animatedValue as Float
                setPosition(
                    (startX + (x - startX) * f).roundToInt(),
                    (startY + (y - startY) * f).roundToInt()
                )
            }
            start()
        }
    }

    private fun setPosition(x: Int, y: Int) {
        if (params.x == x && params.y == y) return
        params.x = x
        params.y = y
        updateLayoutParams()
    }

    private fun positionMapper() = WindowPosition(
        gravity = params.gravity,
        layoutDirection = flyView.layoutDirection,
        frame = if (params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0)
            windowManager.displayBounds(appContext) else screenBounds,
        w = flyView.width,
        h = flyView.height,
    )

    /** Moves the view to where it should rest: inside the screen, and on an edge if [autoSnap] */
    internal fun settle(
        autoSnap: Boolean,
        vx: Float = 0f,
        vy: Float = 0f,
        animated: Boolean = true,
        duration: Long = snapDuration,
    ) {
        if (!attached) return
        val w = flyView.width
        val h = flyView.height
        if (w == 0 || h == 0) {
            pendingSnap = autoSnap // not laid out yet, done by onWindowSizeChanged
            return
        }
        pendingSnap = false
        val m = positionMapper()
        val b = screenBounds
        val x = m.absX(params.x)
        val y = m.absY(params.y)
        val minX = b.left
        val maxX = max(minX, b.right - w)
        val targetY = (y + vy * FLING_PROJECTION).roundToInt()
            .coerceIn(b.top, max(b.top, b.bottom - h))
        val targetX: Int
        if (autoSnap) {
            val toRight = x + w / 2f + vx * FLING_PROJECTION > b.exactCenterX()
            snappedSide = if (toRight) Side.Right else Side.Left
            targetX = if (toRight) (b.right - w - edgePaddingPx).coerceAtLeast(minX)
            else (b.left + edgePaddingPx).coerceAtMost(maxX)
        } else {
            snappedSide = Side.None
            targetX = x.coerceIn(minX, maxX)
        }
        val px = m.paramX(targetX)
        val py = m.paramY(targetY)
        if (animated) moveTo(px, py, duration) else {
            animator?.cancel()
            setPosition(px, py)
        }
    }

    /** Keeps the view on its edge and inside the screen after a size or configuration change */
    private fun reanchor() {
        if (!attached || draggableCount == 0 || dragging || animator?.isRunning == true) return
        val w = flyView.width
        val h = flyView.height
        if (w == 0 || h == 0) return
        val m = positionMapper()
        val b = screenBounds
        val x = when (snappedSide) {
            Side.Left -> b.left + edgePaddingPx
            Side.Right -> b.right - w - edgePaddingPx
            Side.None -> m.absX(params.x)
        }.coerceIn(b.left, max(b.left, b.right - w))
        val y = m.absY(params.y).coerceIn(b.top, max(b.top, b.bottom - h))
        setPosition(m.paramX(x), m.paramY(y))
    }

    private fun onWindowSizeChanged() {
        if (!attached || draggableCount == 0) return
        if (pendingSnap) settle(autoSnap = true, animated = false) else reanchor()
    }

    internal fun draggableStarted() {
        draggableCount++
    }

    internal fun draggableStopped() {
        draggableCount--
    }

    // dragging

    internal fun beginDrag() {
        if (!attached || removing) return
        animator?.cancel()
        dragging = true
        snappedSide = Side.None
        dragStartX = params.x
        dragStartY = params.y
        dismissZone?.let { DismissOverlay.show(appContext, windowManager, it) }
    }

    /** Moves the view by [dx], [dy] screen pixels from where the drag started */
    internal fun dragBy(dx: Float, dy: Float) {
        if (!dragging) return
        val m = positionMapper()
        val d = windowManager.displayBounds(appContext)
        val w = flyView.width
        val h = flyView.height
        val x = m.absX(dragStartX + (if (m.flipX) -dx else dx).roundToInt())
            .coerceIn(d.left, max(d.left, d.right - w))
        val y = m.absY(dragStartY + (if (m.flipY) -dy else dy).roundToInt())
            .coerceIn(d.top, max(d.top, d.bottom - h))
        setPosition(m.paramX(x), m.paramY(y))
        updateDismissHover(d, m.absY(params.y) + h / 2f)
    }

    private fun updateDismissHover(d: Rect, centerY: Float) {
        val zone = dismissZone ?: return
        val over = centerY >= d.bottom - d.height() * zone.heightFraction
        if (over == isOverDismissZone) return
        isOverDismissZone = over
        DismissOverlay.setHovered(over)
        if (over && zone.hapticFeedback) {
            flyView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    internal fun endDrag(vx: Float, vy: Float, autoSnap: Boolean) {
        if (!dragging) return
        dragging = false
        val dismiss = isOverDismissZone
        isOverDismissZone = false
        DismissOverlay.hide()
        if (dismiss) removeView() else settle(autoSnap, vx, vy)
    }

    private companion object {
        const val TAG = "FlyViewInfo"

        /** How far (in seconds) the fling velocity is projected to pick the edge to snap to */
        const val FLING_PROJECTION = 0.12f
    }
}
