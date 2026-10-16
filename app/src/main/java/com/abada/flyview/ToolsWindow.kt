package com.abada.flyview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.abada.flyView.overlay.FlyEdge
import com.abada.flyView.overlay.FlyPlacement
import com.abada.flyView.overlay.FlySize
import com.abada.flyView.overlay.FlyVisibility
import com.abada.flyView.overlay.FlyWindow

/**
 * One window that aggregates what screens contribute with `FlyItem`. It hides when it is empty.
 * It is docked at the top of the screen. The items are laid out here, with a dismiss button that
 * this content draws, the library draws none.
 */
@Composable
fun ToolsWindow() {
    FlyWindow(
        key = "tools",
        hideWhenEmpty = true,
        visibleWhen = FlyVisibility.Foreground,
        placement = FlyPlacement.Docked(FlyEdge.Top, size = FlySize.Wrap),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFDDEEFF))
                .padding(8.dp)
        ) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.Content()
                    Button(onClick = item::dismiss) { Text("x") }
                }
            }
        }
    }
}
