package com.abada.flyView

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.abada.flyView.windowManagerUtils.isInCloseZone

/**
 * This method makes your flyView draggable
 *
 * @param autoGoToBorder If it is `false` the view will stay where the user left it
 * @param duration The amount of time needed to move the view to the border of the screen in milliseconds
 * @param content The content of the draggable flyView
 */
@Composable
fun <T : FlyController> FlyViewInfo<T>.DraggableFlyView(
    autoGoToBorder: Boolean = true,
    duration: Long = 300,
    content: @Composable () -> Unit
) = Box(
    modifier = Modifier.pointerInput(autoGoToBorder, duration) {
        detectDragGestures(onDragEnd = {
            if (!removeIfInCloseZone() && autoGoToBorder)
                goToScreenBorder(duration)
        }) { change, dragAmount ->
            change.consume()
            cancelAnimation()
            val (x, y) = dragAmount.x.toInt() to dragAmount.y.toInt()
            params.x += x
            params.y += y
            updateLayoutParams()
        }
    },
) {
    LaunchedEffect(key1 = autoGoToBorder, key2 = duration, block = { if (autoGoToBorder) goToScreenBorder(duration) })
    content()
}
private fun <T : FlyController> FlyViewInfo<T>.removeIfInCloseZone(): Boolean {
    var out = false
    if (isInCloseZone(params.y)) {
        removeView()
        out = true
    }
    return out
}