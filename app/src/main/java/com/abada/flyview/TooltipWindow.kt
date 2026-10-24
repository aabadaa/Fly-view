package com.abada.flyview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abada.flyView.overlay.FlyTooltipHost
import com.abada.flyView.overlay.FlyWindow
import com.abada.flyView.overlay.flyTooltip
import com.abada.flyview.ui.theme.FLyViewTheme

/**
 * A window to try tooltips: long press a button to see what it does. `TooltipBox` can't show inside a
 * floating window (it needs an activity), `FlyTooltipHost` draws the tooltip in a window of its own.
 *
 * Drag the window to the top of the screen: the tooltip goes below the button when it has no room above,
 * and drag it to a side: it stays inside the screen.
 */
@Composable
fun TooltipWindow() {
    FlyTooltipHost {
        FlyWindow(
            key = "tooltip",
            onDismissRequest = { CounterState.isTooltipOpen.value = false },
        ) {
            FLyViewTheme {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = {}, modifier = Modifier.flyTooltip("Long press me")) { Text("Hold") }
                    Button(
                        onClick = {},
                        modifier = Modifier.flyTooltip("A longer sentence: it wraps and stays on the screen"),
                    ) { Text("Edge") }
                    Button(onClick = { dismiss() }, modifier = Modifier.flyTooltip("Close")) { Text("×") }
                }
            }
        }
    }
}
