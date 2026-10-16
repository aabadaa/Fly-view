package com.abada.flyView.core

/** The user did not allow this app to draw over other apps. Ask for it with `FlyOverlay.requestPermission`. */
class OverlayPermissionException : IllegalStateException(
    "The overlay permission is missing. Call FlyOverlay.requestPermission first."
)

/** The system refused to start the foreground service (for example from the background on Android 12+). */
class FlyServiceStartException(cause: Throwable) : IllegalStateException(
    "The foreground service could not be started. Start the overlay from a visible activity, " +
            "for example with FlyLaunchActivity.", cause
)

/** A window was composed before `FlyOverlay.start` was called. */
class FlyOverlayNotStartedException : IllegalStateException(
    "FlyOverlay is not started. Call FlyOverlay.start before showing a FlyWindow."
)
