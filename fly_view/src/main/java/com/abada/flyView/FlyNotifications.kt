package com.abada.flyView

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import androidx.core.app.NotificationCompat

/** The default foreground notification, shared by [FlyViewService] and the overlay's `FlyService` */
internal object FlyNotifications {
    fun createChannel(service: Service, channelId: String) {
        val manager = service.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Floating windows", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun build(service: Service, channelId: String): Notification {
        val info = service.applicationInfo
        return NotificationCompat.Builder(service, channelId)
            .setSmallIcon(info.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info)
            .setContentTitle(info.loadLabel(service.packageManager))
            .setContentText("Showing a floating window")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
