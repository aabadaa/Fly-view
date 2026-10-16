package com.abada.flyView.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.abada.flyView.core.AnchoredPosition
import com.abada.flyView.core.ClosingReason
import com.abada.flyView.core.DismissCoordinator
import com.abada.flyView.core.DismissReason
import com.abada.flyView.core.DockEdge
import com.abada.flyView.core.Dim
import com.abada.flyView.core.EdgeSide
import com.abada.flyView.core.FlyPoint
import com.abada.flyView.core.FlyRect
import com.abada.flyView.core.FlySizePx
import com.abada.flyView.core.FlyVelocity
import com.abada.flyView.core.HAnchor
import com.abada.flyView.core.PlacementResolver
import com.abada.flyView.core.PlacementSpec
import com.abada.flyView.core.PositionResolver
import com.abada.flyView.core.PositionSpec
import com.abada.flyView.core.SizeSpec
import com.abada.flyView.core.SnapPolicy
import com.abada.flyView.core.VAnchor
import kotlinx.coroutines.CoroutineScope
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything a window is configured with, compared on every recomposition */
internal data class WindowConfig(
    val focusable: Boolean = false,
    val drag: FlyDrag = FlyDrag.Whole,
    val snap: FlySnap = FlySnap.Edges(),
    val dismissZone: DismissZone? = DismissZone(),
    val placement: FlyPlacement = FlyPlacement.Floating,
    val ime: FlyIme = FlyIme.Ignore,
)

/**
 * Owns one real window: its params, its position, drag, snap and dismissal.
 * Main thread only. The composable `FlyWindow` drives it.
 */
internal class FlyWindowRuntime(
    val key: String,
    private val appContext: Context,
    scope: CoroutineScope,
    private val parentContext: CompositionContext,
) {
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = appContext.resources.displayMetrics.density
    private var flyView: FlyView? = null
    private var added = false
    private var animator: ValueAnimator? = null

    var config = WindowConfig()
        private set
    var configure: ((WindowManager.LayoutParams) -> Unit)? = null
    var onDismissCommit: (() -> Unit)? = null
    var onClosingSoon: ((ClosingReason) -> Unit)? = null
    var onKeyEvent: ((android.view.KeyEvent) -> Boolean)? = null

    /** `true` once a dismissal was committed and nobody handles `onDismissRequest` */
    var hiddenByDismiss by mutableStateOf(false)
        private set

    /** Why the composable does not want the window shown right now, set on every recomposition */
    @Volatile
    var hiddenReasonTarget: FlyHiddenReason? = null

    /** Whether the composable wants the window shown, set on every recomposition */
    @Volatile
    var shownTarget = false

    /**
     * The window is meant to exist: visible, not empty, not dismissed. It may still be hidden for a while
     * by its visibility rule. The overlay stops itself when no window is alive.
     */
    var wantsToExist = false
        set(value) {
            val changed = field != value
            field = value
            if (value) FlyOverlayHost.markWanted()
            if (changed && !value) FlyOverlayHost.checkEmpty()
        }

    /** Alive: meant to exist, or still on screen playing its exit */
    val isAlive: Boolean get() = wantsToExist || isAttached

    lateinit var state: FlyWindowState
        private set

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).also { it.gravity = Gravity.TOP or Gravity.START }

    private var dragging = false
    private var dragStartX = 0
    private var dragStartY = 0
    private var pendingInitial = false
    private var imeBottom = 0
    private var imeShift = 0

    val isAttached: Boolean get() = flyView != null

    val coordinator: DismissCoordinator = DismissCoordinator(
        scope = scope,
        commit = { reason -> commitDismiss(reason) },
        onCancelled = { settle(FlyVelocity(), animated = true) },
        onError = { Log.e(TAG, "dismiss effect failed in $key", it) },
    )

    private fun commitDismiss(@Suppress("UNUSED_PARAMETER") reason: DismissReason) {
        val handler = onDismissCommit
        if (handler != null) {
            handler()
            // the owner may decide to keep the window: then a later request has to work again
            coordinator.resetSoon { shownTarget }
        } else {
            hiddenByDismiss = true
        }
    }

    /** The window shows again (the composable left and came back, or the owner handled the request) */
    fun resetDismiss() {
        hiddenByDismiss = false
        coordinator.reset()
    }

    // configuration

    fun bind(newState: FlyWindowState) {
        val previous = if (this::state.isInitialized) state else null
        if (previous === newState) return
        previous?.runtime = null
        state = newState
        newState.runtime = this
        if (isAttached) {
            previous?.isShown = false
            newState.isShown = true
            newState.hiddenReason = null
            val target = newState.savedPosition()
            if (target != null) placeSaved(animated = previous != null) else initialPlacement()
        }
    }

    fun update(newConfig: WindowConfig) {
        val changed = newConfig != config
        config = newConfig
        if (isAttached && changed) {
            applyFrame()
            updateParams()
        }
    }

    // window

    fun show(content: @Composable () -> Unit) {
        if (isAttached) return
        val view = FlyView(
            context = appContext,
            // true consumes the key, anything else lets it go on to the content and the back handling
            keyDispatcher = { event -> if (event != null && onKeyEvent?.invoke(event) == true) true else null },
            parentContext = parentContext,
            onBackFallback = { coordinator.request(DismissReason.Back) },
            content = content,
        )
        view.onConfigChanged = { reanchor() }
        view.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) onWindowSizeChanged()
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            applyImeShift()
            insets
        }
        flyView = view
        applyFrame()
        try {
            windowManager.addView(view, params)
        } catch (e: RuntimeException) {
            // the permission can be revoked at any time: don't stay "attached" with a view that has no window
            flyView = null
            view.dispose()
            throw e
        }
        added = true
        if (this::state.isInitialized) {
            state.isShown = true
            state.hiddenReason = null
        }
        if (isFloating() && this::state.isInitialized) {
            if (state.savedPosition() != null) placeSaved(animated = false) else initialPlacement()
        }
    }

    fun remove(reason: FlyHiddenReason? = null) {
        val view = flyView ?: return
        flyView = null
        added = false
        if (this::state.isInitialized) {
            state.isShown = false
            state.hiddenReason = reason ?: hiddenReasonTarget ?: FlyHiddenReason.Visibility
        }
        animator?.cancel()
        if (dragging) endDragCleanup()
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "remove: the window was already removed")
        }
        view.dispose()
    }

    fun release() {
        remove(FlyHiddenReason.LeftComposition)
        if (this::state.isInitialized && state.runtime === this) state.runtime = null
    }

    fun notifyClosing(reason: ClosingReason) {
        onClosingSoon?.invoke(reason)
    }

    // params

    private fun isFloating() = config.placement == FlyPlacement.Floating

    private fun Dp.px(): Int = (value * density).roundToInt()

    private fun placementSpec(): PlacementSpec = when (val p = config.placement) {
        FlyPlacement.Floating -> PlacementSpec.Floating
        FlyPlacement.Fullscreen -> PlacementSpec.Fullscreen
        is FlyPlacement.Docked -> PlacementSpec.Docked(
            edge = when (p.edge) {
                FlyEdge.Top -> DockEdge.Top
                FlyEdge.Bottom -> DockEdge.Bottom
                FlyEdge.Start -> DockEdge.Start
                FlyEdge.End -> DockEdge.End
            },
            size = when (val s = p.size) {
                FlySize.Wrap -> SizeSpec.Wrap
                FlySize.Fill -> SizeSpec.Fill
                is FlySize.Fixed -> SizeSpec.Fixed(s.size.px())
                is FlySize.Fraction -> SizeSpec.Fraction(s.value)
            },
            margin = p.margin.px(),
            respectInsets = p.insets,
        )
    }

    private fun applyFrame() {
        val floating = isFloating()
        val usable = windowManager.usableBounds(appContext)
        val frame = PlacementResolver.resolve(
            placement = placementSpec(),
            floating = FlyPoint(params.x, params.y),
            floatingH = HAnchor.Start,
            floatingV = VAnchor.Top,
            usable = usable.toCore(),
        )
        if (floating) {
            // absolute from the top-left, `configure` runs after this and may change it
            params.gravity = Gravity.TOP or Gravity.START
        } else {
            params.gravity = gravity(frame.h, frame.v)
            params.x = frame.x
            params.y = frame.y
        }
        params.width = frame.width.toParam()
        params.height = frame.height.toParam()

        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (floating) {
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else if (frame.ignoreInsets) {
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        if (!config.focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.flags = flags

        params.softInputMode = when {
            config.ime == FlyIme.Ignore -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
            floating -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING // moved by applyImeShift
            else -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        configure?.invoke(params)
        if (floating && this::state.isInitialized && isAttached) {
            state.savedPosition()?.let { place(it) }
        }
    }

    private fun gravity(h: HAnchor, v: VAnchor): Int =
        (when (h) {
            HAnchor.Start -> Gravity.START
            HAnchor.End -> Gravity.END
            HAnchor.Center -> Gravity.CENTER_HORIZONTAL
        }) or (when (v) {
            VAnchor.Top -> Gravity.TOP
            VAnchor.Bottom -> Gravity.BOTTOM
            VAnchor.Center -> Gravity.CENTER_VERTICAL
        })

    private fun Dim.toParam(): Int = when (this) {
        Dim.Wrap -> WindowManager.LayoutParams.WRAP_CONTENT
        Dim.Match -> WindowManager.LayoutParams.MATCH_PARENT
        is Dim.Px -> value
    }

    private fun updateParams() {
        val view = flyView ?: return
        if (!added) return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "updateParams: the window was already removed")
        }
    }

    // position

    private val windowSize: FlySizePx
        get() = FlySizePx(flyView?.width ?: 0, flyView?.height ?: 0)

    private fun mapper(): AnchoredPosition {
        val inScreen = params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0
        val frame = if (inScreen) windowManager.displayBounds(appContext) else windowManager.usableBounds(appContext)
        val size = windowSize
        return anchoredPosition(
            params.gravity, flyView?.layoutDirection ?: View.LAYOUT_DIRECTION_LTR, frame, size.width, size.height
        )
    }

    private fun currentAbs(): FlyPoint {
        val m = mapper()
        return FlyPoint(m.absX(params.x), m.absY(params.y))
    }

    /** Puts the window at the absolute [abs] position (keeps the keyboard shift) */
    private fun place(abs: FlyPoint) {
        val m = mapper()
        val x = m.paramX(abs.x)
        val y = m.paramY(abs.y - imeShift)
        if (params.x == x && params.y == y) return
        params.x = x
        params.y = y
        updateParams()
    }

    private fun setPosition(abs: FlyPoint, side: EdgeSide? = state.snappedSide) {
        state.position = abs
        state.snappedSide = side
        place(abs)
    }

    private fun usable(): FlyRect = windowManager.usableBounds(appContext).toCore()

    private fun snapPaddingPx(): Int? = when (val s = config.snap) {
        FlySnap.None -> null
        is FlySnap.Edges -> s.padding.px()
    }

    private fun snapDuration(): Long = (config.snap as? FlySnap.Edges)?.durationMillis ?: 300L

    private fun initialPlacement() {
        val size = windowSize
        if (size.isEmpty) {
            pendingInitial = true
            return
        }
        pendingInitial = false
        val spec = when (val initial = state.initial) {
            FlyPosition.Center -> PositionSpec.Center
            is FlyPosition.Absolute -> PositionSpec.Absolute(initial.x, initial.y)
            is FlyPosition.Edge -> PositionSpec.Edge(initial.side, initial.y, initial.padding.px())
            is FlyPosition.InheritFrom -> initial.state.position?.let { PositionSpec.Absolute(it.x, it.y) }
                ?: PositionSpec.Center
        }
        val abs = PositionResolver.resolve(spec, size, usable())
        val side = (spec as? PositionSpec.Edge)?.side
        setPosition(abs, side)
        if (side == null && snapPaddingPx() != null) settle(FlyVelocity(), animated = false)
    }

    /** Back to the saved position of the state, flush to its edge if it was snapped */
    private fun placeSaved(animated: Boolean) {
        if (!isFloating()) return
        val size = windowSize
        if (size.isEmpty) {
            pendingInitial = state.savedPosition() == null
            return
        }
        val saved = state.savedPosition() ?: return
        val target = SnapPolicy.reanchor(saved, size, usable(), state.snappedSide, snapPaddingPx() ?: 0)
        if (animated) moveTo(target, state.snappedSide, 300) else setPosition(target)
    }

    fun animateTo(owner: FlyWindowState, x: Int, y: Int, durationMillis: Long) {
        if (!isAttached || owner !== state || !isFloating()) return
        moveTo(SnapPolicy.clamp(FlyPoint(x, y), windowSize, usable()), null, durationMillis)
    }

    fun snapNow(owner: FlyWindowState, durationMillis: Long) {
        if (owner !== state) return
        settle(FlyVelocity(), animated = true, duration = durationMillis, forceSnap = true)
    }

    private fun moveTo(target: FlyPoint, side: EdgeSide?, durationMillis: Long) {
        animator?.cancel()
        animator = null
        val start = state.position ?: currentAbs()
        if (durationMillis <= 0 || !isAttached || start == target) {
            setPosition(target, side)
            return
        }
        state.snappedSide = side
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMillis
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val f = it.animatedValue as Float
                val p = FlyPoint(
                    (start.x + (target.x - start.x) * f).roundToInt(),
                    (start.y + (target.y - start.y) * f).roundToInt(),
                )
                state.position = p
                place(p)
            }
            start()
        }
    }

    /** Where the window rests: inside the screen, on an edge if the snap says so */
    private fun settle(
        velocity: FlyVelocity,
        animated: Boolean,
        duration: Long = snapDuration(),
        forceSnap: Boolean = false,
    ) {
        if (!isAttached || !isFloating()) return
        val size = windowSize
        if (size.isEmpty) {
            pendingInitial = true
            return
        }
        val pos = state.position ?: currentAbs()
        val padding = snapPaddingPx() ?: if (forceSnap) 0 else null
        val settled = SnapPolicy.settle(pos, size, usable(), velocity, padding)
        if (animated) moveTo(settled.position, settled.side, duration) else setPosition(settled.position, settled.side)
    }

    private fun reanchor() {
        if (!isAttached || !isFloating() || dragging || animator?.isRunning == true) return
        val size = windowSize
        if (size.isEmpty || !this::state.isInitialized) return
        val pos = state.position ?: return
        setPosition(SnapPolicy.reanchor(pos, size, usable(), state.snappedSide, snapPaddingPx() ?: 0))
    }

    private fun onWindowSizeChanged() {
        if (!isAttached || !this::state.isInitialized || !isFloating()) return
        if (pendingInitial) {
            if (state.savedPosition() == null) initialPlacement() else placeSaved(animated = false)
        } else {
            reanchor()
        }
    }

    // keyboard

    private fun applyImeShift() {
        if (!isAttached || !isFloating() || config.ime != FlyIme.Avoid || dragging) {
            if (imeShift != 0) {
                imeShift = 0
                state.savedPosition()?.let { place(it) }
            }
            return
        }
        val abs = state.savedPosition() ?: return
        val display = windowManager.displayBounds(appContext)
        val windowBottom = abs.y + windowSize.height
        val shift = if (imeBottom > 0) max(0, windowBottom - (display.bottom - imeBottom)) else 0
        if (shift != imeShift) {
            imeShift = shift
            place(abs)
        }
    }

    // drag

    val canDrag: Boolean get() = isAttached && isFloating() && config.drag != FlyDrag.None

    /** The pointer in screen pixels, `null` if the window is not shown */
    fun pointerOnScreen(): androidx.compose.ui.geometry.Offset? =
        flyView?.let { androidx.compose.ui.geometry.Offset(it.pointerX, it.pointerY) }

    fun beginDrag() {
        if (!canDrag || hiddenByDismiss || coordinator.state != DismissCoordinator.State.Idle) return
        animator?.cancel()
        dragging = true
        imeShift = 0
        state.isDragging = true
        state.snappedSide = null
        dragStartX = params.x
        dragStartY = params.y
        config.dismissZone?.let { DismissOverlay.show(appContext, windowManager, it) }
    }

    fun dragBy(dx: Float, dy: Float) {
        if (!dragging) return
        val m = mapper()
        val display = windowManager.displayBounds(appContext)
        val size = windowSize
        val abs = SnapPolicy.clamp(
            FlyPoint(
                m.absX(dragStartX + (if (m.flipX) -dx else dx).roundToInt()),
                m.absY(dragStartY + (if (m.flipY) -dy else dy).roundToInt()),
            ),
            size, display.toCore(),
        )
        setPosition(abs, null)
        val zone = config.dismissZone ?: return
        val over = SnapPolicy.isOverDismissZone(abs, size, display.toCore(), zone.heightFraction)
        if (over != state.isOverDismissZone) {
            state.isOverDismissZone = over
            DismissOverlay.setHovered(over)
            if (over && zone.hapticFeedback) flyView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun endDrag(vx: Float, vy: Float) {
        if (!dragging) return
        val dismiss = state.isOverDismissZone && config.dismissZone != null
        endDragCleanup()
        if (dismiss) {
            if (!coordinator.request(DismissReason.DragDrop)) settle(FlyVelocity(vx, vy), animated = true)
        } else {
            settle(FlyVelocity(vx, vy), animated = true)
            if (state.persistContext != null) state.save()
        }
    }

    private fun endDragCleanup() {
        dragging = false
        state.isDragging = false
        state.isOverDismissZone = false
        DismissOverlay.hide()
    }

    companion object {
        private const val TAG = "FlyWindow"
    }
}
