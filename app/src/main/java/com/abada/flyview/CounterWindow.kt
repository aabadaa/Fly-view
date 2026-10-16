package com.abada.flyview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abada.flyView.overlay.FlyWindow
import com.abada.flyview.ui.theme.FLyViewTheme
import kotlinx.coroutines.flow.update

/**
 * The floating counter. It reads the same state as the screen of the app.
 * Drag it, it snaps to the closest edge. Drop it on the bottom or press the cross to close it.
 */
@Composable
fun CounterWindow() {
    FlyWindow(
        key = "counter",
        onDismissRequest = { CounterState.isOpen.value = false },
    ) {
        val count by CounterState.count.collectAsState()
        FLyViewTheme {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.LightGray)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "$count", modifier = Modifier.padding(horizontal = 12.dp))
                Button(onClick = { CounterState.count.update { it + 1 } }) { Text("+") }
                Button(onClick = { dismiss() }) { Text("×") }
            }
        }
    }
}
