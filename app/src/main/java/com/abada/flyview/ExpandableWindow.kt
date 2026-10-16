package com.abada.flyview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * A floating window that expands into a panel and collapses back to a small header.
 * The whole window is a drag handle, the size changes but the window keeps its place (and its edge).
 */
@Composable
fun ExpandableWindow() {
    FlyWindow(
        key = "expandable",
        onDismissRequest = { CounterState.isExpandableOpen.value = false },
    ) {
        val expanded by CounterState.expanded.collectAsState()
        val count by CounterState.count.collectAsState()
        FLyViewTheme {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.LightGray)
                    .padding(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (expanded) "Panel" else "Count $count", modifier = Modifier.padding(horizontal = 12.dp))
                    Button(onClick = { CounterState.expanded.update { !it } }) { Text(if (expanded) "Collapse" else "Expand") }
                }
                // A window is a real window: the system resizes it a frame after the content, so animating the
                // size (animateContentSize, expand/shrink) re-wraps the text and clips the content on every step.
                // Fade instead: the window resizes once and the content fades.
                AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                    Column(modifier = Modifier.width(240.dp).padding(top = 8.dp)) {
                        Text("Drag me anywhere, the window follows the finger.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$count", modifier = Modifier.padding(end = 12.dp))
                            Button(onClick = { CounterState.count.update { it + 1 } }) { Text("+") }
                            Button(onClick = { dismiss() }) { Text("×") }
                        }
                    }
                }
            }
        }
    }
}
