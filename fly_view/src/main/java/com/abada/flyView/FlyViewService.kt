package com.abada.flyView

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import com.abada.flyView.windowManagerUtils.addFlyInfo
import com.abada.flyView.windowManagerUtils.removeFlyView

/**
 * A foreground service that keeps your fly views alive when your app is not on the screen.
 * Show a view with it using `FlyViews.show(context, key, MyService::class.java)`.
 *
 * Extend this class (the body can be empty) and declare the subclass in your manifest:
 *
 * ```xml
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
 *
 * <service android:name=".MyService" android:exported="false"
 *     android:foregroundServiceType="specialUse">
 *     <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
 *         android:value="shows a floating window" />
 * </service>
 * ```
 *
 * The service stops itself when the last view it showed is removed. Override [createNotification]
 * to customize the notification that Android requires for a foreground service.
 */
abstract class FlyViewService : Service() {
    private lateinit var wm: WindowManager
    private val activeKeys = mutableSetOf<String>()
    private var destroyed = false

    /** The id of the foreground notification */
    protected open val notificationId: Int get() = 1

    /** The notification shown while a view is showing. Override it to use your own. */
    protected open fun createNotification(): Notification = FlyNotifications.build(this, CHANNEL_ID)

    override fun onCreate() {
        super.onCreate()
        wm = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
        FlyNotifications.createChannel(this, CHANNEL_ID)
    }

    /**
     * Shows the requested FlyView. The views can't be restored after the process dies,
     * so the service is not sticky.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android kills the app if a service that was started as foreground doesn't call this
        startForeground(notificationId, createNotification())

        val key = intent?.getStringExtra(FlyViews.EXTRA_KEY)
        if (key == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        val extras = intent.extras?.let { Bundle(it).apply { remove(FlyViews.EXTRA_KEY) } }
        if (!FlyViews.isShown(key) && !showView(key)) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        if (extras != null && !extras.isEmpty) FlyViews.update(key, extras)
        // the view may belong to someone else (added directly): then this service owns nothing
        stopIfIdle()
        return START_NOT_STICKY
    }

    private fun showView(key: String): Boolean {
        val provider = FlyViews.infoProviders[key]
        if (provider == null) {
            Log.e(TAG, "nothing is registered for \"$key\", call FlyViews.register first")
            return false
        }
        val shown = wm.addFlyInfo(this, key, provider(), onRemoved = {
            activeKeys.remove(key)
            stopIfIdle()
        })
        if (shown) activeKeys.add(key)
        return shown
    }

    private fun stopIfIdle() {
        if (destroyed || activeKeys.isNotEmpty()) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        val keys = activeKeys.toList()
        activeKeys.clear()
        keys.forEach { wm.removeFlyView(it) }
        super.onDestroy()
    }

    /** This service does not support binding. */
    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private const val TAG = "FlyViewService"
        private const val CHANNEL_ID = "flyViewService"

        /** Same as [FlyViews.infoProviders] */
        val infoProviders get() = FlyViews.infoProviders

        /** Same as [FlyViews.show] with a service */
        fun show(
            context: Context,
            key: String,
            serviceClass: Class<out FlyViewService>,
            bundle: Bundle? = null,
        ): Boolean = FlyViews.show(context, key, serviceClass, bundle)
    }
}
