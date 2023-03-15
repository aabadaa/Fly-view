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

class FlyViewService : Service() {
    private lateinit var wm: WindowManager
    private val ID = "flyViewService"
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel(
            ID, ID,
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, ID)
            .build()
        val key = intent!!.getStringExtra("key")!!

        startForeground(1, notification)
        showView(key)
        intent.extras?.let {
            updateFlyView(key, it)
        }
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
            wm.addFlyInfo(this@FlyViewService, key, this)
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        val infoProviders = mutableMapOf<String, () -> FlyViewInfo<in FlyController>>()

        fun show(context: Context, key: String, bundle: Bundle? = null) {
            Intent(context, FlyViewService::class.java).also {
                it.putExtra("key", key)
                bundle?.run { it.putExtras(this) }
                context.startForegroundService(it)
            }
        }

        fun update(context: Context, key: String, bundle: Bundle) {
            Intent(context, FlyViewService::class.java).also {
                it.putExtra("key", key)
                it.putExtras(bundle)
                context.startForegroundService(it)
            }
        }
    }
}