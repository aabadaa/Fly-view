package com.abada.flyView

import android.animation.ObjectAnimator
import android.content.ContentValues.TAG
import android.content.res.Resources
import android.util.Log
import android.util.Property
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 *
 * This method makes your flyView draggable
 * @param content- the content of the draggable flyView
 */
@Composable
fun FlyViewScope.DraggableFlyView(
    autoGoToBorder: Boolean = true,
    duration: Long = 300,
    content: @Composable () -> Unit
) = Box(
    modifier = Modifier.pointerInput(Unit) {
        detectDragGestures(onDragEnd = {
            if (autoGoToBorder) {
                val screenWidth = Resources.getSystem().displayMetrics.widthPixels
                val xProperty =
                    object : Property<WindowManager.LayoutParams, Int>(Int::class.java, "x") {
                        override fun get(params: WindowManager.LayoutParams): Int {
                            return params.x
                        }

                        override fun set(params: WindowManager.LayoutParams, value: Int) {
                            params.x = value
                            this@DraggableFlyView.params = params
                        }
                    }
                ObjectAnimator.ofInt(
                    params, xProperty, if (params.x < 0) -screenWidth / 2 else screenWidth / 2
                ).apply {
                    this.duration = duration
                    start()
                }
                this@DraggableFlyView.params = params
            }
        }) { change, dragAmount ->
            change.consume()
            val (x, y) = dragAmount.x.toInt() to dragAmount.y.toInt()
            val params = params
            params.x += x
            params.y += y
            this@DraggableFlyView.params = params
            Log.i(TAG, "DraggableFlyView: ${params.x}")
        }
    },
) {
    content()
}
