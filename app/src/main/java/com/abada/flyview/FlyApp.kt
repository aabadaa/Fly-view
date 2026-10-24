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
        FlyOverlay.contribute("expandable") {
            val open by CounterState.isExpandableOpen.collectAsState()
            if (open) ExpandableWindow()
        }
        FlyOverlay.contribute("playground") {
            val open by CounterState.isPlaygroundOpen.collectAsState()
            if (open) PlaygroundWindow()
        }
        FlyOverlay.contribute("popups") {
            val open by CounterState.isPopupsOpen.collectAsState()
            if (open) PopupsWindow()
        }
        FlyOverlay.contribute("tooltip") {
            val open by CounterState.isTooltipOpen.collectAsState()
            if (open) TooltipWindow()
        }
    }
}
