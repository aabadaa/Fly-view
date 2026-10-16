package com.abada.flyview

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.abada.flyView.overlay.FlyOverlay

class FlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // composed by the overlay while it runs, in the process and not in an activity
        FlyOverlay.contribute("counter") {
            val open by CounterState.isOpen.collectAsState()
            if (open) CounterWindow()
        }
    }
}
