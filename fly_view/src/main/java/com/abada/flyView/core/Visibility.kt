package com.abada.flyView.core

enum class LifecycleLevel { Destroyed, Created, Started, Resumed }

/** When a window or an item is shown */
sealed interface Visibility {
    /** Until it is removed */
    data object Always : Visibility

    /** While the app is in the foreground */
    data object Foreground : Visibility

    /** While the app is not in the foreground */
    data object Background : Visibility

    /** While the owner (an activity, a fragment, a service...) is at least at [min] */
    data class Owner(val ownerId: Any, val min: LifecycleLevel = LifecycleLevel.Started) : Visibility
}

/** What the visibility rules are evaluated against */
data class VisibilityConditions(
    val appInForeground: Boolean,
    val owners: Map<Any, LifecycleLevel> = emptyMap(),
)

fun Visibility.isVisible(conditions: VisibilityConditions): Boolean = when (this) {
    Visibility.Always -> true
    Visibility.Foreground -> conditions.appInForeground
    Visibility.Background -> !conditions.appInForeground
    is Visibility.Owner -> (conditions.owners[ownerId] ?: LifecycleLevel.Destroyed) >= min
}
