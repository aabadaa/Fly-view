package com.abada.flyView

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class FlyViewService : Service() {

    private val showedViews: MutableMap<String, FlyView> = mutableMapOf()
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
        else FlyView.infos[key]?.run {
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
                wm.updateViewLayout(showedViews[key], params)
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
            addView(key, flyView, params)
        } ?: run {
            throw java.lang.NullPointerException("there is no view linked with the  key $key.")
        }
    }

    private fun addView(key: String, view: FlyView, params: WindowManager.LayoutParams) {
        if (showedViews.containsKey(key))
            return
        showedViews[key] = view
        wm.addView(view, params)
    }

    private fun removeView(key: String) {
        val flyView = showedViews.remove(key)!!
        flyView.removeView(flyView.rootView)
        wm.removeView(flyView)
        if(showedViews.isEmpty())
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
    }
}

