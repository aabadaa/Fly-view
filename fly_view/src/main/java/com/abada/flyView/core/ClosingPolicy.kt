package com.abada.flyView.core

/** Why a window may close soon. A hint for the user, never a guarantee. */
sealed interface ClosingReason {
    /** The system is short on memory and the process is a candidate to be killed */
    data class ProcessAtRisk(val trimLevel: Int) : ClosingReason

    /** The owner of the window is stopping */
    data object OwnerStopping : ClosingReason

    /** The overlay is stopping */
    data object HostStopping : ClosingReason
}

object ClosingPolicy {
    // Same values as ComponentCallbacks2
    const val TRIM_MEMORY_BACKGROUND = 40

    /**
     * @param serviceActive a foreground service keeps the process alive, so it is not at risk
     * @return the reason, or `null` if the process is not at risk
     */
    fun reasonForTrim(level: Int, serviceActive: Boolean): ClosingReason? =
        if (!serviceActive && level >= TRIM_MEMORY_BACKGROUND) ClosingReason.ProcessAtRisk(level) else null
}
