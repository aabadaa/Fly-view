package com.abada.flyView

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.*

private val showedViews: MutableMap<String, FlyViewInfo<out FlyController>> = mutableMapOf()

fun <T : FlyController> WindowManager.addFlyInfo(
    context: Context,
    key: String,
    flyViewInfo: FlyViewInfo<T>
) {
    if (!Settings.canDrawOverlays(context))
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).also {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })

    if (showedViews.containsKey(key))
        return
    showedViews[key] = flyViewInfo

    flyViewInfo.run {
        val runRecomposeScope = CoroutineScope(AndroidUiDispatcher.CurrentThread)
        val flyScope = FlyViewScope(
            params = params,
            removeView = {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(100)// if there is an animation the app will crash , so I delayed a little to wait the animation to finih
                    runRecomposeScope.cancel()
                    this@addFlyInfo.removeFlyView(key)
                }
            },
            updateLayoutParams = {
                updateViewLayout(flyView, it)
            }
        )
        val flyView = FlyView(
            context = context,
            runRecomposeScope = runRecomposeScope,
            keyDispatcher = keyDispatcher,
            content = {
                Box(modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val (x, y) = dragAmount.x.toInt() to dragAmount.y.toInt()
                        val params = flyScope.params
                        params.x += x
                        params.y += y
                        flyScope.params = params

                    }
                }) {
                    flyScope.content()
                }
            }
        )
        this.flyView = flyView
        addView(flyView, params)
    }
}

fun WindowManager.removeFlyView(key: String) {
    val flyViewInfo = showedViews.remove(key)!!
    flyViewInfo.flyView.run {
        removeView(rootView)
        this@removeFlyView.removeView(this)
    }
}

fun updateFlyView(key: String, bundle: Bundle): Boolean =
    showedViews[key]?.controller?.update(bundle) == Unit
