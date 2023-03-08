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
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class FlyViewService : Service() {
    private val showedViews: MutableMap<String, FlyViewInfo<*>> = mutableMapOf()
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
            showedViews[key]!!.controller.update(it)
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
        if (!Settings.canDrawOverlays(this))
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
        else (FlyView.infoProviders[key]?.invoke() as? FlyViewInfo<FlyController>)?.run {
            val runRecomposeScope = CoroutineScope(AndroidUiDispatcher.CurrentThread)
            val flyScope = FlyViewScope(
                params = params,
                controller = controller,
                removeView = {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(100)// if there is an animation the app will crash , so I delayed a little to wait the animation to finih
                        runRecomposeScope.cancel()
                        this@FlyViewService.removeView(key)
                    }
                }
            ) {
                wm.updateViewLayout(flyView, params)
            }

            val flyView = FlyView(
                context = this@FlyViewService,
                runRecomposeScope = runRecomposeScope,
                onDragChanged = { x, y ->
                    params.x += x
                    params.y += y
                    wm.updateViewLayout(this, params)
                },
                keyDispatcher = keyDispatcher,
                content = {
                    flyScope.content()
                }
            )
            this.flyView = flyView
            addView(key, this, params)
        } ?: run {
            throw java.lang.NullPointerException("there is no view linked with the  key $key.")
        }
    }

    private fun addView(key: String, viewInfo: FlyViewInfo<*>, params: WindowManager.LayoutParams) {
        if (showedViews.containsKey(key))
            return
        showedViews[key] = viewInfo
        wm.addView(viewInfo.flyView, params)
    }

    private fun removeView(key: String) {
        val flyViewInfo = showedViews.remove(key)!!
        flyViewInfo.flyView.run {
            removeView(rootView)
            wm.removeView(this)
        }
        if (showedViews.isEmpty())
            stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        fun show(context: Context, key: String) {
            Intent(context, FlyViewService::class.java).also {
                it.putExtra("key", key)
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

