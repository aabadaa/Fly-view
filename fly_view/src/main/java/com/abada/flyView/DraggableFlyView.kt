package com.abada.flyView

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun FlyViewScope.DraggableFlyView(content: @Composable () -> Unit) =
    Box(
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val (x, y) = dragAmount.x.toInt() to dragAmount.y.toInt()
                val params = params
                params.x += x
                params.y += y
                this@DraggableFlyView.params = params

            }
        },
    ) {
        content()
    }
