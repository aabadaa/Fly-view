package com.abada.flyview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.abada.flyView.overlay.FlyOverlay
import com.abada.flyview.ui.theme.FLyViewTheme
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FLyViewTheme {
                MainScreen(onShow = ::showCounter, onShowExpandable = ::showExpandable, onHide = { FlyOverlay.requestDismiss("counter") })
            }
        }
    }

    private fun showExpandable() {
        lifecycleScope.launch {
            val allowed = FlyOverlay.hasPermission(this@MainActivity) ||
                    FlyOverlay.requestPermission(this@MainActivity)
            if (!allowed) return@launch
            CounterState.isExpandableOpen.value = true
            FlyOverlay.start(this@MainActivity)
        }
    }

    /** Ask for the permission first, then start the overlay. `start` throws if the permission is missing. */
    private fun showCounter() {
        lifecycleScope.launch {
            val allowed = FlyOverlay.hasPermission(this@MainActivity) ||
                    FlyOverlay.requestPermission(this@MainActivity)
            if (!allowed) return@launch
            CounterState.isOpen.value = true
            FlyOverlay.start(this@MainActivity)
        }
    }
}

@Composable
private fun MainScreen(onShow: () -> Unit, onShowExpandable: () -> Unit, onHide: () -> Unit) {
    // the floating window reads the same state: press + here or there, both show the same number
    val count by CounterState.count.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Count", style = MaterialTheme.typography.titleMedium)
            Text("$count", style = MaterialTheme.typography.displayLarge)
            Button(onClick = { CounterState.count.update { it + 1 } }) { Text("+1") }
            Button(onClick = onShow) { Text("Show the floating counter") }
            Button(onClick = onShowExpandable) { Text("Show the expandable window") }
            Button(onClick = onHide) { Text("Hide it") }
            Text("Go to another app: the counter stays on top.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
