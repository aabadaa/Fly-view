package com.abada.flyView.overlay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.abada.flyView.core.FlyServiceStartException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * An invisible activity that starts the overlay from a visible context. On Android 12 and later a
 * foreground service can not be started from the background, but it can from a visible activity,
 * so a widget, a notification action or a tile points to a subclass of this one.
 *
 * ```kotlin
 * class LaunchActivity : FlyLaunchActivity() {
 *     override suspend fun onLaunch(intent: Intent) {
 *         AppState.open(intent.getIntExtra("index", -1))
 *         FlyOverlay.start(this)
 *     }
 *     override fun onPermissionMissing() = toast("Allow drawing over other apps")
 * }
 * ```
 *
 * Declare it with `android:theme="@android:style/Theme.Translucent.NoTitleBar"`,
 * `android:excludeFromRecents="true"` and `android:noHistory="true"`.
 */
abstract class FlyLaunchActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Start the overlay here. It runs after the permission check and may suspend. */
    protected abstract suspend fun onLaunch(intent: Intent)

    /** The permission is missing: the settings screen opens after this returns. */
    protected open fun onPermissionMissing() {}

    /** The system refused to start the service even from this activity. The default rethrows. */
    protected open fun onStartFailed(error: FlyServiceStartException) {
        throw error
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!FlyOverlay.hasPermission(this)) {
            onPermissionMissing()
            startActivity(FlyOverlay.permissionIntent(this))
            finish()
            return
        }
        scope.launch {
            try {
                onLaunch(intent)
            } catch (e: FlyServiceStartException) {
                onStartFailed(e)
            } finally {
                finish()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
