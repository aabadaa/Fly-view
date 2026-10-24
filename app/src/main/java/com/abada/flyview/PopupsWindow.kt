package com.abada.flyview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abada.flyView.core.PopupAlignment
import com.abada.flyView.core.PopupSide
import com.abada.flyView.overlay.FlyBottomSheet
import com.abada.flyView.overlay.FlyDropdownMenu
import com.abada.flyView.overlay.FlyDropdownMenuItem
import com.abada.flyView.overlay.FlyIme
import com.abada.flyView.overlay.FlyPopup
import com.abada.flyView.overlay.FlySnackbarHost
import com.abada.flyView.overlay.FlySnackbarResult
import com.abada.flyView.overlay.FlyTextToolbarProvider
import com.abada.flyView.overlay.FlyTooltipHost
import com.abada.flyView.overlay.FlyWindow
import com.abada.flyView.overlay.flyTooltip
import com.abada.flyView.overlay.flyAnchor
import com.abada.flyView.overlay.rememberFlyAnchor
import com.abada.flyView.overlay.rememberFlyImeOverlap
import com.abada.flyView.overlay.rememberFlySnackbarState
import com.abada.flyview.ui.theme.FLyViewTheme
import kotlinx.coroutines.launch

/**
 * One window to try every popup of the library. `Popup`, `DropdownMenu`, `TooltipBox`, the system text
 * toolbar and `ModalBottomSheet` need an activity window and fail in a floating window; these do not.
 *
 * Drag the window to the top, the bottom and the sides of the screen, with and without the keyboard:
 * the popups must flip to the side that has room and stay inside the screen.
 *
 * - **Popup / Menu**: tap the buttons, tap outside to close.
 * - **Tooltip**: long press "Hold me".
 * - **Text toolbar**: type, double tap a word: cut/copy/paste/select all show next to it.
 * - **Snackbar**: shows above every window, "Undo" reports back.
 * - **Sheet**: opens above the keyboard, tap the scrim or press back to close.
 * - **IME overlap**: the number is how far this window reaches under the keyboard (it should stay 0
 *   while the window can be lifted above the keyboard).
 */
@Composable
fun PopupsWindow() {
    val snackbars = rememberFlySnackbarState()
    FlyTooltipHost {
        FlySnackbarHost(snackbars)
        FlyWindow(
            key = "popups",
            focusable = true,
            ime = FlyIme.Avoid,
            onDismissRequest = { CounterState.isPopupsOpen.value = false },
        ) {
            FLyViewTheme {
                FlyTextToolbarProvider {
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Popups")
                        PopupDemo()
                        MenuDemo()
                        Button(onClick = {}, modifier = Modifier.flyTooltip("A tooltip, long press")) { Text("Hold me") }
                        ToolbarDemo()
                        SnackbarDemo(snackbars)
                        SheetDemo()
                        ImeOverlapDemo()
                        Button(onClick = { dismiss() }) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupDemo() {
    val anchor = rememberFlyAnchor()
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }, modifier = Modifier.flyAnchor(anchor)) { Text("Popup") }
    if (open) {
        FlyPopup(anchor, onDismissRequest = { open = false }, side = PopupSide.Top, alignment = PopupAlignment.Center) {
            Text(
                "A popup above the button, or below when there is no room",
                modifier = Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF3C4))
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun MenuDemo() {
    val anchor = rememberFlyAnchor()
    var open by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf("-") }
    Button(onClick = { open = true }, modifier = Modifier.flyAnchor(anchor)) { Text("Menu: $last") }
    if (open) {
        FlyDropdownMenu(anchor, onDismissRequest = { open = false }) {
            listOf("Share", "Rename", "Delete").forEach { item ->
                FlyDropdownMenuItem(item, onClick = { last = item; open = false })
            }
            FlyDropdownMenuItem("Disabled", onClick = {}, enabled = false)
        }
    }
}

@Composable
private fun ToolbarDemo() {
    var text by remember { mutableStateOf("Select some of this text") }
    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text toolbar") })
}

@Composable
private fun SnackbarDemo(snackbars: com.abada.flyView.overlay.FlySnackbarState) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("-") }
    Button(onClick = {
        scope.launch {
            result = when (snackbars.showSnackbar("Note deleted", actionLabel = "Undo")) {
                FlySnackbarResult.ActionPerformed -> "undone"
                FlySnackbarResult.Dismissed -> "gone"
            }
        }
    }) { Text("Snackbar: $result") }
}

@Composable
private fun SheetDemo() {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }) { Text("Bottom sheet") }
    if (open) {
        FlyBottomSheet(onDismissRequest = { open = false }) {
            var text by remember { mutableStateOf("") }
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A sheet above the keyboard")
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Type here") })
                Button(onClick = { open = false }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun ImeOverlapDemo() {
    val overlap by rememberFlyImeOverlap()
    Text("Reaches under the keyboard: $overlap px")
}
