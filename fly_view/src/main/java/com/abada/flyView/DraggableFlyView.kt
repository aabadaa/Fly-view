package com.abada.flyView

import android.content.res.Resources
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.abada.flyView.windowManagerUtils.animateTo

/**
 *
 * This method makes your flyView draggable
 * @param autoGoToBorder if it is ``false`` the view will stay where the user left it
 * @param duration the amount of time needed to move the view to the border of the screen
 * @param content the content of the draggable flyView
 */
@Composable
fun <T : FlyController> FlyViewInfo<T>.DraggableFlyView(
    autoGoToBorder: Boolean = true,
    duration: Long = 300,
    content: @Composable () -> Unit
) = Box(
    modifier = Modifier.pointerInput(autoGoToBorder, duration) {
        detectDragGestures(onDragEnd = {
            if (!removeOnIconTouch() && autoGoToBorder)
                goToBorder()
        }) { change, dragAmount ->
            change.consume()
            val (x, y) = dragAmount.x.toInt() to dragAmount.y.toInt()
            params.x += x
            params.y += y
            updateLayoutParams()
        }
    },
) {
    LaunchedEffect(key1 = autoGoToBorder, block = { if(autoGoToBorder)goToBorder() })
    content()
}

private fun <T : FlyController> FlyViewInfo<T>.goToBorder() {
    val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    params.animateTo(
        x = if (params.x < 0) -screenWidth / 2 else screenWidth / 2
    ) {
        updateLayoutParams()
    }
}

private fun <T : FlyController> FlyViewInfo<T>.removeOnIconTouch(): Boolean {
    var out = false
    if (params.y > Resources.getSystem().displayMetrics.heightPixels / 6) {
        removeView()
        out = true
    }
    return out
}