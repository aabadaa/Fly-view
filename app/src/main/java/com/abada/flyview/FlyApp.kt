package com.abada.flyview

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.abada.flyView.overlay.FlyOverlay

class FlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FlyOverlay.configure(service = ExampleFlyService::class.java)   // optional keep-alive

        // composed by the overlay while it runs, in the process and not in an activity
        FlyOverlay.contribute("bubble") {
            val open by BubbleEntry.isOpen.collectAsState()
            if (open) BubbleWindow()
        }
        FlyOverlay.contribute("tools") { ToolsWindow() }
    }
}
