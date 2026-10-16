package com.abada.flyView.overlay

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Keeps the process alive while windows show: a foreground service with a notification.
 * It is not an owner of the windows, they hide with their `FlyVisibility`.
 * It stops itself when the overlay stops (the last window is gone).
 *
 * Extend it (the body can be empty), declare it in the manifest and pass it to
 * `FlyOverlay.configure(service = ...)`:
 *
 * ```xml
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
 *
 * <service android:name=".MyFlyService" android:exported="false"
 *     android:foregroundServiceType="specialUse">
 *     <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
 *         android:value="shows a floating window" />
 * </service>
 * ```
 */
abstract class FlyService : Service() {
    /** The id of the foreground notification */
    protected open val notificationId: Int get() = 1

    /** The notification shown while the overlay runs. Override it to use your own. */
    protected open fun createNotification(): Notification = FlyNotifications.build(this, CHANNEL_ID)

    override fun onCreate() {
        super.onCreate()
        FlyNotifications.createChannel(this, CHANNEL_ID)
        FlyOverlayHost.attachService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android kills the app if a service that was started as foreground doesn't call this
        startForeground(notificationId, createNotification())
        if (!FlyOverlayHost.isRunning) stopNow()
        return START_NOT_STICKY
    }

    internal fun stopNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        FlyOverlayHost.detachService(this)
        super.onDestroy()
    }

    /** This service does not support binding. */
    override fun onBind(intent: Intent): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "flyOverlayService"
    }
}
