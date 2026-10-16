package com.abada.flyView.overlay

import android.os.Handler
import android.os.Looper
import com.abada.flyView.core.DismissCoordinator

private const val RESET_AFTER_COMMIT_MS = 600L
private val mainHandler = Handler(Looper.getMainLooper())

/**
 * After the owner handled a committed dismissal it may keep the window or item: give it a moment to
 * leave, then let a later request work again. [stillWanted] is checked when the time is up.
 */
internal fun DismissCoordinator.resetSoon(stillWanted: () -> Boolean = { true }) {
    mainHandler.postDelayed({ if (stillWanted()) reset() }, RESET_AFTER_COMMIT_MS)
}
