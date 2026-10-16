package com.abada.flyview

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.abada.flyView.core.FlyServiceStartException
import com.abada.flyView.core.OverlayPermissionException
import com.abada.flyView.overlay.FlyItem
import com.abada.flyView.overlay.FlyOverlay
import com.abada.flyview.ui.theme.FLyViewTheme
import kotlinx.coroutines.launch

/** Fly-view demo: a bubble window, and a window that aggregates items from this screen */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FLyViewTheme {
                MainScreen(
                    onStartOverlay = { lifecycleScope.launch { ensureStarted() } },
                    onOpenBubble = {
                        lifecycleScope.launch {
                            // suspends until the request is queued, it is kept until the window collects it
                            if (ensureStarted()) BubbleEntry.request("hello")
                        }
                    },
                )
            }
        }
    }

    /**
     * The happy path: ask for the permission first, then start.
     * `FlyOverlay.start` throws if the permission is missing or the system refuses the service.
     */
    private suspend fun ensureStarted(): Boolean {
        if (!FlyOverlay.hasPermission(this) && !FlyOverlay.requestPermission(this)) return false
        return try {
            FlyOverlay.start(this)
            true
        } catch (e: OverlayPermissionException) {
            false
        } catch (e: FlyServiceStartException) {
            Toast.makeText(this, "The system refused the service: ${e.cause?.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
}

@Composable
private fun MainScreen(onStartOverlay: () -> Unit, onOpenBubble: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        var showTimer by remember { mutableStateOf(false) }
        var showTip by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Overlay", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onStartOverlay) { Text("Start the overlay") }
            Button(onClick = onOpenBubble) { Text("Open the bubble") }
            Button(onClick = { FlyOverlay.requestDismiss("bubble") }) { Text("Ask the bubble to dismiss (from outside)") }
            Button(onClick = { FlyOverlay.stop() }) { Text("Stop the overlay") }

            Text("Items: each screen contributes to one window", style = MaterialTheme.typography.titleMedium)
            Switch(checked = showTimer, onCheckedChange = { showTimer = it })
            Text("a timer item")
            Switch(checked = showTip, onCheckedChange = { showTip = it })
            Text("a tip item")
            // composed only while the overlay runs, they leave with this screen
            if (FlyOverlay.isRunning) {
                if (showTimer) FlyItem("tools", "timer", order = 0, onDismissRequest = { showTimer = false }) {
                    Text("Timer 00:42")
                }
                if (showTip) FlyItem("tools", "tip", order = 1, onDismissRequest = { showTip = false }) {
                    Text("Tip: drop the bubble on the bottom")
                }
            }
        }
    }
}
