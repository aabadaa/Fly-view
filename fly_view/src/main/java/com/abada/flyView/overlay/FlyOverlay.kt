package com.abada.flyView.overlay

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.abada.flyView.core.FlyServiceStartException
import com.abada.flyView.core.OverlayPermissionException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * The entry point of the overlay. Everything is main thread only.
 *
 * ```
 * // once, for example in Application.onCreate
 * FlyOverlay.configure(service = MyFlyService::class.java)       // optional keep-alive
 * FlyOverlay.contribute("player") {                               // composes while the overlay runs
 *     if (state.isOpen) FlyWindow("player") { Player() }
 * }
 *
 * // from anywhere: the developer asks for the permission first, start throws if it is missing
 * if (FlyOverlay.hasPermission(context) || FlyOverlay.requestPermission(activity)) FlyOverlay.start(context)
 * ```
 *
 * The overlay stops itself when its last window is gone (see [configure]) and the next
 * [start] composes the contributions again.
 */
object FlyOverlay {
    /** `true` while the overlay runs */
    val isRunning: Boolean get() = FlyOverlayHost.isRunning

    /**
     * @param service a [FlyService] to keep the process alive while windows show (priority and notification).
     * Not an owner: windows still hide with their [FlyVisibility].
     * @param stopWhenEmpty stop the overlay (and the service) when the last window is gone
     */
    fun configure(service: Class<out FlyService>? = null, stopWhenEmpty: Boolean = true) {
        FlyOverlayHost.serviceClass = service
        FlyOverlayHost.stopWhenEmpty = stopWhenEmpty
    }

    /**
     * Starts the overlay: the [contribute] blocks are composed and windows can show.
     *
     * @throws OverlayPermissionException if the user did not allow drawing over other apps
     * @throws FlyServiceStartException if the system refuses to start the configured service
     * (from the background on Android 12+): start it from a visible activity, for example with [FlyLaunchActivity]
     */
    fun start(context: Context, service: Class<out FlyService>? = FlyOverlayHost.serviceClass) {
        if (!hasPermission(context)) throw OverlayPermissionException()
        val app = context.applicationContext
        if (service != null) {
            try {
                app.startForegroundService(Intent(app, service))
            } catch (e: IllegalStateException) {
                throw FlyServiceStartException(e)
            } catch (e: SecurityException) {
                throw FlyServiceStartException(e)
            }
        }
        FlyOverlayHost.start(app)
    }

    /** Removes every window and stops the service. The contributions stay registered for the next [start]. */
    fun stop() = FlyOverlayHost.stop()

    /**
     * Registers a composable block that the overlay composes while it runs, in the process and not in
     * an activity. Put the windows that no screen owns here (the ones a tile or a receiver opens)
     * and let your app state decide whether they show. A block with the same [id] replaces the old one.
     */
    fun contribute(id: String, content: @Composable () -> Unit): FlyContribution =
        FlyContribution(id, FlyOverlayHost.contribute(id, content))

    // dismissal from outside

    /**
     * Asks the window [window] to dismiss, like the user dropping it on the dismiss zone: its
     * `FlyDismissEffect` runs first, then `onDismissRequest`. Returns `false` if there is no such window.
     */
    fun requestDismiss(window: String): Boolean {
        val runtime = FlyOverlayHost.window(window) ?: return false
        runtime.coordinator.request(com.abada.flyView.core.DismissReason.External)
        return true
    }

    /** Asks an item of a window to dismiss. Returns `false` if there is no such item. */
    fun requestDismiss(window: String, itemId: String): Boolean =
        FlyOverlayHost.requestDismissItem(window, itemId)

    /** Asks every window and item to dismiss */
    fun requestDismissAll() = FlyOverlayHost.requestDismissAll()

    // permission

    /** Whether the user allowed this app to draw over other apps */
    fun hasPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** An intent that opens the "draw over other apps" settings of this app */
    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    /**
     * Opens the settings screen and returns what the user decided. Returns `true` right away if the
     * permission is already granted.
     */
    suspend fun requestPermission(activity: ComponentActivity): Boolean =
        suspendCancellableCoroutine { continuation ->
            requestPermission(activity) { granted -> if (continuation.isActive) continuation.resume(granted) }
        }

    fun requestPermission(activity: ComponentActivity, onResult: (granted: Boolean) -> Unit) {
        if (hasPermission(activity)) {
            onResult(true)
            return
        }
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = activity.activityResultRegistry.register(
            "flyView-overlay-permission-${UUID.randomUUID()}",
            ActivityResultContracts.StartActivityForResult()
        ) {
            launcher?.unregister()
            onResult(hasPermission(activity))
        }
        launcher.launch(permissionIntent(activity))
    }

    /**
     * Why the previous process of this app ended (`ApplicationExitInfo.REASON_*`), `null` if unknown
     * or below Android 11. Use it at the next start to tell the user that the system closed the overlay.
     */
    fun lastExitReason(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()?.reason
    }
}

/** A block registered with [FlyOverlay.contribute] */
class FlyContribution internal constructor(private val id: String, private val token: Long) {
    /** Removes the block (its windows leave). Does nothing if another block replaced it. */
    fun dispose() = FlyOverlayHost.removeContribution(id, token)
}
