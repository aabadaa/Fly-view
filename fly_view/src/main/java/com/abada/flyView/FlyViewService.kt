package com.abada.flyView

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.abada.flyView.FlyViewService.Companion.show
import com.abada.flyView.windowManagerUtils.addFlyInfo
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
        val channel = NotificationChannel(
            ID, ID, NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, ID).build()
        val key = intent!!.getStringExtra("key")!!
        startForeground(1, notification)
        showView(key)
        intent.extras?.let {
            updateFlyView(key, it)
        }
        numberOfShowedViews++

        return START_STICKY_COMPATIBILITY
    }

    /**
     * Called when the service is first created.
     * Initializes the WindowManager for displaying FlyViews.
     */
    override fun onCreate() {
        super.onCreate()
        wm = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
    }

    /**
     * Shows a FlyView with the specified key.
     * Retrieves the FlyViewInfo from infoProviders and adds it to the WindowManager.
     *
     * @param key The identifier for the FlyView to show
     */
    private fun showView(
        key: String
    ) {
        (infoProviders[key]!!.invoke() as FlyViewInfo<FlyController>).run {
            val onRemove = onRemove
            wm.addFlyInfo(this@FlyViewService, key, this.copy(onRemove = {
                onRemove()
                numberOfShowedViews--
                if (numberOfShowedViews == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }))
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
                Uri.parse("package:${context.packageName}")
            ).also {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
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