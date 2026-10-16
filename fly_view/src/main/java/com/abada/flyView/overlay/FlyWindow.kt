package com.abada.flyView.overlay

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.abada.flyView.DismissZone
import com.abada.flyView.core.DismissCoordinator
import com.abada.flyView.core.DismissReason
import com.abada.flyView.core.FlyOverlayNotStartedException
import com.abada.flyView.core.LifecycleLevel
import com.abada.flyView.core.Visibility
import com.abada.flyView.core.VisibilityConditions
import com.abada.flyView.core.isVisible

/** What the content of a [FlyWindow] can see and do */
@Stable
interface FlyWindowScope {
    val key: String
    val state: FlyWindowState

    /** The items that screens contributed to this window with [FlyItem], in display order */
    val items: List<FlyItemHandle>

    /** A hint that the window may close soon, `null` most of the time. Never a guarantee. */
    val closingSoon: FlyClosingReason?

    /** Asks to dismiss this window. Runs your [FlyDismissEffect] first, if you have one. */
    fun dismiss()
}

/** The window of the current content. Use it where the receiver of [FlyWindow] content is not at hand. */
val LocalFlyWindow = staticCompositionLocalOf<FlyWindowScope> {
    error("There is no FlyWindow around this composable")
}

internal val LocalFlyDismissTarget = compositionLocalOf<DismissCoordinator?> { null }

internal class FlyWindowScopeImpl(
    override val key: String,
    override val state: FlyWindowState,
    val runtime: FlyWindowRuntime,
) : FlyWindowScope {
    override val items: List<FlyItemHandle>
        get() = FlyOverlayHost.itemsOf(key).filter { it.isShown }.map { FlyItemHandle(it) }

    override val closingSoon: FlyClosingReason? get() = FlyOverlayHost.closingReason

    override fun dismiss() {
        runtime.coordinator.request(DismissReason.Content)
    }
}

/** An item that a screen contributed to a window */
@Stable
class FlyItemHandle internal constructor(private val entry: FlyItemEntry) {
    val id: String get() = entry.id

    /** Draws the content of the item */
    @Composable
    fun Content() {
        CompositionLocalProvider(LocalFlyDismissTarget provides entry.coordinator) {
            entry.content()
        }
    }

    /** Asks to dismiss this item. Runs the [FlyDismissEffect] of the item first, if it has one. */
    fun dismiss() {
        entry.coordinator.request(DismissReason.Content)
    }
}

internal class FlyItemEntry(
    val window: String,
    val id: String,
    scope: kotlinx.coroutines.CoroutineScope,
    private val onDismissRequest: () -> (() -> Unit)?,
    val content: @Composable () -> Unit,
) {
    var order by mutableStateOf(0)
    var allowed by mutableStateOf(true)
    var hiddenByDismiss by mutableStateOf(false)
    val isShown: Boolean get() = allowed && !hiddenByDismiss

    val coordinator = DismissCoordinator(
        scope = scope,
        commit = {
            val handler = onDismissRequest()
            if (handler != null) {
                handler()
                // the owner may decide to keep the item: then a later request has to work again
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ coordinator.reset() }, 600)
            } else {
                hiddenByDismiss = true
            }
            FlyOverlayHost.publishItems(window)
        },
    )
}

/**
 * A floating window.
 *
 * It is a small real window that wraps its content, so touches outside of it reach the app below.
 * It is shown while it is in the composition (leaving the composition removes it right away),
 * while [visible] is `true`, and while its [visibleWhen] rule says so. `visible = false` plays [exit]
 * and then removes it, [onRemoved] is called when it has gone.
 *
 * The overlay has to be started with `FlyOverlay.start` before a window is composed,
 * otherwise [FlyOverlayNotStartedException] is thrown.
 *
 * Several windows can coexist, a window with the same [key] as another one replaces it.
 *
 * @param key identifies the window
 * @param state the position of the window, one per mode if it remembers a position for each of them
 * @param visibleWhen when it is shown, by default as long as the owner of the caller (its activity or
 * fragment) is started, or while the app is in the foreground for a `FlyOverlay.contribute` block
 * @param focusable `true` if the window takes the focus: needed for text fields and the back button,
 * and it takes the back button and the keyboard from the app below. `false` for a display only widget.
 * @param drag which part of the window drags it
 * @param snap what happens when the user lets go of the window
 * @param dismissZone the area at the bottom where the window is dropped to dismiss it, `null` for none
 * @param placement floating, docked to an edge, or fullscreen
 * @param ime what happens when the keyboard shows
 * @param hideWhenEmpty `true` hides the window when no [FlyItem] is contributed to it
 * @param onDismissRequest called when a dismissal is committed (after your [FlyDismissEffect]):
 * update your state here. Without it the window hides until it leaves the composition and comes back.
 * @param onRemoved called once after the window has gone because [visible] became `false`
 * @param onClosingSoon called with a reason when the window may close soon
 * @param configure last resort: change the raw window manager params, called whenever they are built
 * @param content the content, its receiver gives the items, `dismiss()` and `closingSoon`
 */
@Composable
fun FlyWindow(
    key: String,
    state: FlyWindowState = rememberFlyWindowState(key),
    visible: Boolean = true,
    visibleWhen: FlyVisibility = FlyVisibility.Default,
    focusable: Boolean = false,
    drag: FlyDrag = FlyDrag.Whole,
    snap: FlySnap = FlySnap.Edges(),
    dismissZone: DismissZone? = DismissZone(),
    placement: FlyPlacement = FlyPlacement.Floating,
    ime: FlyIme = FlyIme.Ignore,
    hideWhenEmpty: Boolean = false,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    onDismissRequest: (() -> Unit)? = null,
    onRemoved: (() -> Unit)? = null,
    onClosingSoon: ((FlyClosingReason) -> Unit)? = null,
    configure: ((WindowManager.LayoutParams) -> Unit)? = null,
    content: @Composable FlyWindowScope.() -> Unit,
) {
    // fail fast: composing a window before the overlay started is a bug of the caller
    remember { if (!FlyOverlayHost.isRunning) throw FlyOverlayNotStartedException() }

    val appContext = LocalContext.current.applicationContext
    val parent = rememberCompositionContext()
    val scope = rememberCoroutineScope()
    val runtime = remember(key) { FlyWindowRuntime(key, appContext, scope, parent) }
    val windowScope = remember(runtime, state) { FlyWindowScopeImpl(key, state, runtime) }
    val currentContent by rememberUpdatedState(content)
    val currentDrag by rememberUpdatedState(drag)
    val currentEnter by rememberUpdatedState(enter)
    val currentExit by rememberUpdatedState(exit)
    val currentOnRemoved by rememberUpdatedState(onRemoved)
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val transition = remember(runtime) { MutableTransitionState(false) }

    val allowed = rememberVisibilityAllowed(visibleWhen) { runtime.notifyClosing(FlyClosingReason.OwnerStopping) }
    val hasItems = windowScope.items.isNotEmpty()
    val shown = visible && allowed && FlyOverlayHost.isRunning &&
            (!hideWhenEmpty || hasItems) && !runtime.hiddenByDismiss

    SideEffect {
        runtime.configure = configure
        runtime.onClosingSoon = onClosingSoon
        runtime.onDismissCommit = if (currentDismiss != null) ({ currentDismiss?.invoke() }) else null
        runtime.shownTarget = shown
        runtime.wantsToExist = visible && (!hideWhenEmpty || hasItems) && !runtime.hiddenByDismiss
        runtime.bind(state)
        runtime.update(WindowConfig(focusable, drag, snap, dismissZone, placement, ime))
    }

    // the window leaves the composition: gone right away, no exit animation
    DisposableEffect(runtime) {
        val token = FlyOverlayHost.registerWindow(key, runtime)
        onDispose {
            FlyOverlayHost.unregisterWindow(key, token)
            runtime.release()
        }
    }

    // a dismissal that nobody handles hides the window until it comes back
    LaunchedEffect(visible) { if (visible) runtime.resetDismiss() }

    if (shown) {
        SideEffect {
            if (!runtime.isAttached) {
                runtime.show {
                    CompositionLocalProvider(
                        LocalFlyWindow provides windowScope,
                        LocalFlyDismissTarget provides runtime.coordinator,
                    ) {
                        AnimatedVisibility(
                            visibleState = transition,
                            enter = currentEnter,
                            exit = currentExit,
                        ) {
                            val whole = if (currentDrag == FlyDrag.Whole) Modifier.flyDragHandle() else Modifier
                            Box(modifier = whole) { windowScope.currentContent() }
                        }
                    }
                }
            }
            transition.targetState = true
        }
    } else {
        SideEffect { transition.targetState = false }
    }

    // after the exit animation: take the window away
    LaunchedEffect(transition.isIdle, transition.currentState, shown) {
        if (!shown && transition.isIdle && !transition.currentState && runtime.isAttached) {
            runtime.remove()
            if (!visible || runtime.hiddenByDismiss) currentOnRemoved?.invoke()
            FlyOverlayHost.checkEmpty()
        }
    }
}

/**
 * Takes control of the dismissal of the enclosing [FlyWindow] or [FlyItem]. [block] runs when
 * anyone asks to dismiss it: the user dropped the window on the dismiss zone, the back button,
 * `FlyOverlay.requestDismiss`, or `dismiss()` of the content. Do what you want (show a message,
 * save, ask for a confirmation...) and finish with `dismiss()` (the real dismissal) or `cancel()`.
 * Returning without either counts as `dismiss()`.
 *
 * It is registered like `DisposableEffect`: it is removed when it leaves the composition, and
 * registered again when [keys] change.
 */
@Composable
fun FlyDismissEffect(vararg keys: Any?, block: suspend FlyDismissScope.() -> Unit) {
    val target = LocalFlyDismissTarget.current
        ?: error("FlyDismissEffect has to be inside a FlyWindow or a FlyItem")
    val currentBlock by rememberUpdatedState(block)
    DisposableEffect(target, *keys) {
        target.effect = { currentBlock(this) }
        onDispose { target.effect = null }
    }
}

/**
 * Contributes [content] to the window [window], from any screen or module. Items with the same
 * ([window], [id]) replace each other. The window decides how to lay them out with `items`.
 *
 * @param order items are sorted by it, then by the time they were added
 * @param onDismissRequest called when a dismissal of the item is committed, update your state there.
 * Without it the item hides until it leaves the composition and comes back.
 */
@Composable
fun FlyItem(
    window: String,
    id: String,
    order: Int = 0,
    visibleWhen: FlyVisibility = FlyVisibility.Default,
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentContent by rememberUpdatedState(content)
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val entry = remember(window, id) {
        FlyItemEntry(window, id, scope, { currentDismiss?.let { d -> { d() } } }) { currentContent() }
    }
    val allowed = rememberVisibilityAllowed(visibleWhen) {}
    SideEffect {
        entry.order = order
        entry.allowed = allowed
    }
    DisposableEffect(entry) {
        val token = FlyOverlayHost.registerItem(entry)
        onDispose { FlyOverlayHost.unregisterItem(entry, token) }
    }
}

/** Evaluates [rule] for the caller: its lifecycle owner, or the foreground of the app. */
@Composable
private fun rememberVisibilityAllowed(rule: FlyVisibility, onOwnerStopping: () -> Unit): Boolean {
    val isHostBlock = LocalFlyHostBlock.current
    val view = LocalView.current
    val callerOwner = remember(view) { view.findViewTreeLifecycleOwner() }
    val owner: LifecycleOwner? = when {
        rule is FlyVisibility.Owner -> rule.owner
        rule == FlyVisibility.Default && !isHostBlock -> callerOwner
        else -> null
    }
    val min = (rule as? FlyVisibility.Owner)?.min?.toLevel() ?: LifecycleLevel.Started
    val level by rememberLifecycleLevel(owner, onOwnerStopping)
    val core = when (rule) {
        FlyVisibility.Always -> Visibility.Always
        FlyVisibility.Foreground -> Visibility.Foreground
        FlyVisibility.Background -> Visibility.Background
        FlyVisibility.Default -> if (owner != null) Visibility.Owner(owner, min) else Visibility.Foreground
        is FlyVisibility.Owner -> Visibility.Owner(rule.owner, min)
    }
    val conditions = VisibilityConditions(
        appInForeground = FlyOverlayHost.foreground,
        owners = if (owner != null) mapOf<Any, LifecycleLevel>(owner to level) else emptyMap(),
    )
    return core.isVisible(conditions)
}

@Composable
private fun rememberLifecycleLevel(owner: LifecycleOwner?, onStopping: () -> Unit): State<LifecycleLevel> {
    val level = remember(owner) { mutableStateOf(owner?.lifecycle?.currentState?.toLevel() ?: LifecycleLevel.Resumed) }
    val currentOnStopping by rememberUpdatedState(onStopping)
    DisposableEffect(owner) {
        if (owner == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_STOP) currentOnStopping()
            level.value = source.lifecycle.currentState.toLevel()
        }
        owner.lifecycle.addObserver(observer)
        level.value = owner.lifecycle.currentState.toLevel()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return level
}

private fun Lifecycle.State.toLevel(): LifecycleLevel = when (this) {
    Lifecycle.State.DESTROYED -> LifecycleLevel.Destroyed
    Lifecycle.State.INITIALIZED, Lifecycle.State.CREATED -> LifecycleLevel.Created
    Lifecycle.State.STARTED -> LifecycleLevel.Started
    Lifecycle.State.RESUMED -> LifecycleLevel.Resumed
}
