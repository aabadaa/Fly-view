package com.abada.flyView

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.abada.flyView.FlyViewService.Companion.show
import com.abada.flyView.windowManagerUtils.addFlyInfo
import com.abada.flyView.windowManagerUtils.removeFlyView
import com.abada.flyView.windowManagerUtils.showedViews
import com.abada.flyView.windowManagerUtils.updateFlyView

/**
 * This service starts a foreground service when you call [show] method.
 * This can be used to show a [FlyView] when your activity is not running.
 *
 * Extend this class and implement your own service logic for managing FlyViews
 * in the background. The service automatically manages its lifecycle based on
 * the number of active FlyViews.
 */
abstract class FlyViewService : Service() {
    private lateinit var wm: WindowManager
    private var numberOfShowedViews = 0
    private val ownedKeys = mutableSetOf<String>()

    /**
     * Called when the service is started with an intent.
     * Creates a foreground notification and shows the requested FlyView.
     *
     * @param intent Intent containing the FlyView key and optional data bundle
     * @param flags Additional data about this start request
     * @param startId A unique integer representing this specific request to start
     * @return The return value indicates what semantics the system should use for the service's current started state
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // must be called after startForegroundService even if we end up stopping right away
        startForeground(1, createNotification(ID))

        val key = intent?.getStringExtra("key")
        if (key == null || !infoProviders.containsKey(key)) {
            Log.e(TAG, "onStartCommand: no FlyViewInfo provider registered for key=$key")
            stopIfIdle()
            return START_NOT_STICKY
        }
        if (showView(key)) numberOfShowedViews++
        intent.extras?.let {
            updateFlyView(key, it)
        }
        stopIfIdle()

        // views live only in memory, so there is nothing to restore if the system kills the service
        return START_NOT_STICKY
    }

    private fun stopIfIdle() {
        if (numberOfShowedViews <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Called when the service is first created.
     * Initializes the WindowManager for displaying FlyViews.
     */
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService<WindowManager>()!!
        getSystemService<NotificationManager>()!!.createNotificationChannel(
            NotificationChannel(ID, ID, NotificationManager.IMPORTANCE_LOW)
        )
    }

    /**
     * Override this to customize the foreground notification (icon, title, content intent...).
     * The notification must have a small icon, otherwise the system rejects it.
     *
     * @param channelId The id of the notification channel created by this service
     */
    protected open fun createNotification(channelId: String): Notification =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        // the system can stop the service while views are still attached
        ownedKeys.toList().forEach { wm.removeFlyView(it) }
        ownedKeys.clear()
        super.onDestroy()
    }

    /**
     * Shows a FlyView with the specified key.
     * Retrieves the FlyViewInfo from infoProviders and adds it to the WindowManager.
     *
     * @param key The identifier for the FlyView to show
     * @return true if a new view was added, false if the key is already shown
     */
    private fun showView(
        key: String
    ): Boolean {
        if (showedViews.containsKey(key)) return false
        val provider = infoProviders[key] ?: return false
        return (provider.invoke() as FlyViewInfo<FlyController>).run {
            val onRemove = onRemove
            val added = wm.addFlyInfo(this@FlyViewService, key, this.copy(onRemove = {
                onRemove()
                ownedKeys.remove(key)
                numberOfShowedViews--
                stopIfIdle()
            }))
            if (added) ownedKeys.add(key)
            added
        }
    }

    /**
     * Called when a client binds to the service.
     * This service does not support binding, so this returns null.
     *
     * @param intent The Intent that was used to bind to this service
     * @return null since this service does not support binding
     */
    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        /** Notification channel ID for the foreground service */
        private const val ID = "flyViewService"
        private const val TAG = "FlyViewService"

        /**
         * Add here your [FlyViewInfo] to enable the service to show it when you call [show] methods.
         * Map of FlyView keys to their corresponding FlyViewInfo provider functions.
         */
        val infoProviders = mutableMapOf<String, () -> FlyViewInfo<out FlyController>>()

        /**
         * Call this method to show a [FlyViewInfo] that you added to the [infoProviders].
         * If overlay permission is not granted, it will redirect to the permission settings.
         *
         * @param context A context to start the service
         * @param key The key you used to add your [FlyViewInfo] to the [infoProviders]
         * @param serviceClass The class of your FlyViewService implementation
         * @param bundle An optional bundle that will be passed to your controller [FlyController.update] method
         */
        fun show(
            context: Context,
            key: String,
            serviceClass: Class<out FlyViewService>,
            bundle: Bundle? = null,
        ) {
            if (!Settings.canDrawOverlays(context))
                context.startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            else
                Intent(context, serviceClass).also {
                    it.putExtra("key", key)
                    bundle?.run { it.putExtras(this) }
                    context.startForegroundService(it)
                }
        }
    }
}