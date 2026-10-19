package com.abada.flyview

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.abada.flyView.overlay.FlyWindow
import com.abada.flyview.ui.theme.FLyViewTheme

/**
 * A window to try the keyboard and the layout changes by hand:
 *
 * - **Keyboard**: tap the text field. The window moves up so that all of it is above the keyboard,
 *   and goes back when the keyboard closes. Drag the window while the keyboard is open: it can't go
 *   behind it (except into the drop zone).
 * - **Layout by gesture**: pinch the grey panel to make it taller or smaller, double tap it to jump
 *   between small and large. Do it while the keyboard is open: the window must stay above the keyboard
 *   as it grows and move back down as it shrinks.
 *
 * Try it with the window at the top, in the middle and at the bottom edge of the screen.
 */
@Composable
fun PlaygroundWindow() {
    FlyWindow(
        key = "playground",
        focusable = true, // needed to type
        onDismissRequest = { CounterState.isPlaygroundOpen.value = false },
    ) {
        var text by remember { mutableStateOf("") }
        var panelHeight by remember { mutableFloatStateOf(SMALL) }
        FLyViewTheme {
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Playground", modifier = Modifier.weight(1f))
                    Button(onClick = { dismiss() }) { Text("×") }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Type here") },
                    singleLine = true,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .animateContentSize()
                        .height(panelHeight.dp)
                        .width(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                panelHeight = if (panelHeight > SMALL) SMALL else LARGE
                            })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                panelHeight = (panelHeight * zoom).coerceIn(SMALL, LARGE)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Pinch or double tap\n${panelHeight.toInt()} dp", color = Color.DarkGray)
                }
            }
        }
    }
}

private const val SMALL = 60f
private const val LARGE = 320f
