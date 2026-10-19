package com.abada.flyView.overlay

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.abada.flyView.core.ClosingReason
import com.abada.flyView.core.DismissReason
import com.abada.flyView.core.DismissScope
import com.abada.flyView.core.EdgeSide

typealias FlySide = EdgeSide
typealias FlyDismissScope = DismissScope
typealias FlyDismissReason = DismissReason
typealias FlyClosingReason = ClosingReason

enum class FlyEdge { Top, Bottom, Start, End }

/** How big a window is along its docked edge */
@Immutable
sealed interface FlySize {
    data object Wrap : FlySize
    data object Fill : FlySize
    data class Fixed(val size: Dp) : FlySize
    data class Fraction(val value: Float) : FlySize
}

/** Where a window lives on the screen */
@Immutable
sealed interface FlyPlacement {
    /** A free window at the position of its [FlyWindowState]. Drag and snap apply. */
    data object Floating : FlyPlacement

    /**
     * Stuck to an edge, for example a bar above the keyboard:
     * `Docked(FlyEdge.Bottom, FlySize.Fill)` with `ime = FlyIme.Avoid`.
     * Drag and snap are ignored, the floating position is kept for when it floats again.
     *
     * @param margin the gap to the edge
     * @param insets `true` keeps it out of the system bars, `false` draws edge to edge
     */
    data class Docked(
        val edge: FlyEdge,
        val size: FlySize = FlySize.Wrap,
        val margin: Dp = 0.dp,
        val insets: Boolean = true,
    ) : FlyPlacement

    data object Fullscreen : FlyPlacement
}

/** What a window does when the keyboard shows */
enum class FlyIme {
    /** Nothing, the keyboard may cover the window */
    Ignore,

    /**
     * Stay above the keyboard. A docked bottom window or a fullscreen one resizes. A floating window
     * moves up while the keyboard is visible (Android 11+). The window needs `focusable = true`.
     */
    Avoid,
}

/** Which part of a window drags it */
enum class FlyDrag {
    None,

    /** A drag anywhere in the window moves it, children that consume the gesture win */
    Whole,

    /** Only the areas marked with [flyDragHandle] */
    HandleOnly,
}

/** What happens when the user lets go of a dragged window */
@Immutable
sealed interface FlySnap {
    /** It stays where it is dropped, inside the screen */
    data object None : FlySnap

    /** It flies to the closest horizontal edge, the fling velocity counts */
    data class Edges(val padding: Dp = 0.dp, val durationMillis: Long = 300) : FlySnap
}

/** Why a window is not on the screen */
enum class FlyHiddenReason {
    /** A `visibleWhen` rule: the app or the owner of the window is in the wrong state */
    Visibility,

    /** `visible = false`, after its exit */
    Invisible,

    /** `hideWhenEmpty` and no item is contributed */
    Empty,

    /** A dismissal was committed and nobody handled `onDismissRequest` */
    Dismissed,

    /** The overlay stopped */
    OverlayStopped,

    /** The `FlyWindow` call left the composition */
    LeftComposition,
}

/** When a window or an item is shown */
@Immutable
sealed interface FlyVisibility {
    /**
     * A window that a screen composes: while that screen's activity or fragment is started.
     * A window of a `FlyOverlay.contribute` block: always, so it floats over other apps.
     */
    data object Default : FlyVisibility

    /** Until it is removed */
    data object Always : FlyVisibility

    /** While the app is in the foreground */
    data object Foreground : FlyVisibility

    /** While the app is not in the foreground */
    data object Background : FlyVisibility

    /** While [owner] is at least at [min] */
    data class Owner(val owner: LifecycleOwner, val min: Lifecycle.State = Lifecycle.State.STARTED) : FlyVisibility
}

/** Where a window starts, before the user ever moved it */
@Immutable
sealed interface FlyPosition {
    data object Center : FlyPosition
    data class Absolute(val x: Int, val y: Int) : FlyPosition

    /** Flush to [side], [y] is 0 at the top and 1 at the bottom of the free height */
    data class Edge(val side: FlySide, val y: Float = 0.5f, val padding: Dp = 0.dp) : FlyPosition

    /** The first time, it starts where [state] is. Later it keeps its own position. */
    data class InheritFrom(val state: FlyWindowState) : FlyPosition
}

/**
 * The area at the bottom of the screen where a dragged window is dismissed.
 * While the user drags a window a drop area (see `FlyOverlay.dismissZoneContent`) is shown in it,
 * releasing the window while its center is inside the area asks to dismiss it.
 *
 * @property heightFraction The height of the area as a fraction of the screen height
 * @property hapticFeedback Vibrate a little when the window enters the area
 */
@Immutable
data class DismissZone(
    val heightFraction: Float = 0.18f,
    val hapticFeedback: Boolean = true,
)

@Immutable
data class FlyDialogProperties(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val scrimColor: Color = Color(0x99000000),
    val enter: EnterTransition = fadeIn(),
)
