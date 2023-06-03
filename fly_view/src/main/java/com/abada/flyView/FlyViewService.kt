package com.abada.flyView

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.abada.flyView.FlyViewService.Companion.show
import com.abada.flyView.windowManagerUtils.addFlyInfo
import com.abada.flyView.windowManagerUtils.updateFlyView

/**
 *
 * This service starts a foreground service when you call [show] method
 * this can be used to show a [FlyView] when your activity is not running
 */
class FlyViewService : Service() {
    private lateinit var wm: WindowManager
    private val ID = "flyViewService"
    var numberOfShowedViews = 0
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

    override fun onCreate() {
        super.onCreate()
        wm = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private fun showView(
        key: String
    ) {
        (infoProviders[key]!!.invoke() as FlyViewInfo<FlyController>).run {
            wm.addFlyInfo(this@FlyViewService, key, this.copy(onRemove = {
               this@run.onRemove()
                numberOfShowedViews--
                if (numberOfShowedViews == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }))
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        /**
         * add here your [FlyViewInfo] to enable the service to show it when your call [show] methods
         */
        val infoProviders = mutableMapOf<String, () -> FlyViewInfo<in FlyController>>()

        /**
         * call this method to show a [FlyViewInfo] that you added to the [infoProviders]
         * @param context a context to start the service
         * @param key the key you used to add your [FlyViewInfo] to the [infoProviders]
         * @param bundle an optional bundle that will be passed to your controller [FlyController.update] method
         */
        fun show(context: Context, key: String, bundle: Bundle? = null) {
            Intent(context, FlyViewService::class.java).also {
                it.putExtra("key", key)
                bundle?.run { it.putExtras(this) }
                context.startForegroundService(it)
            }
        }
    }
}