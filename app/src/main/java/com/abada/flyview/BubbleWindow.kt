package com.abada.flyview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abada.flyView.overlay.FlyDismissEffect
import com.abada.flyView.overlay.FlyDrag
import com.abada.flyView.overlay.FlyIme
import com.abada.flyView.overlay.FlyPosition
import com.abada.flyView.overlay.FlySide
import com.abada.flyView.overlay.FlySnap
import com.abada.flyView.overlay.FlyWindow
import com.abada.flyView.overlay.flyDragHandle
import com.abada.flyView.overlay.rememberFlyViewModel
import com.abada.flyView.overlay.rememberFlyWindowState

/**
 * A bubble that snaps to the screen edge and expands into a small panel.
 * Each mode remembers its own position: switching the state animates the window to it.
 */
@Composable
fun BubbleWindow() {
    val vm = rememberFlyViewModel { BubbleViewModel() }   // window session scope
    val bubbleState = rememberFlyWindowState("bubble/collapsed", FlyPosition.Edge(FlySide.Right, y = 0.3f))
    val panelState = rememberFlyWindowState("bubble/expanded", FlyPosition.InheritFrom(bubbleState))

    LaunchedEffect(vm) { BubbleEntry.requests.collect(vm::onRequest) }   // opened again while it shows

    FlyWindow(
        key = "bubble",
        state = if (vm.expanded) panelState else bubbleState,
        visible = !vm.closed,                                            // false plays the exit
        focusable = vm.expanded,                                         // text field and back button
        drag = if (vm.expanded) FlyDrag.HandleOnly else FlyDrag.Whole,
        snap = if (vm.expanded) FlySnap.None else FlySnap.Edges(),
        ime = if (vm.expanded) FlyIme.Avoid else FlyIme.Ignore,
        onDismissRequest = { vm.closed = true },
        onRemoved = { BubbleEntry.isOpen.value = false },                // the session is over
    ) {
        FlyDismissEffect {                                               // drop on the zone, back, requestDismiss...
            vm.showGoodbyeAndSave()
            dismiss()
        }
        BackHandler(vm.expanded) { vm.expanded = false }

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.LightGray)
                .padding(8.dp)
        ) {
            if (vm.expanded) {
                Text("Drag me", modifier = Modifier.flyDragHandle())
                TextField(value = vm.text, onValueChange = { vm.text = it })
                Row {
                    Button(onClick = { vm.count++ }) { Text("count ${vm.count}") }
                    Button(onClick = { vm.expanded = false }) { Text("Collapse") }
                    Button(onClick = ::dismiss) { Text("Close") }
                }
            } else {
                Text(
                    text = if (vm.closing) vm.message else "Bubble ${vm.count}",
                    modifier = Modifier.clickable { vm.expanded = true }.padding(8.dp)
                )
            }
        }
    }
}
