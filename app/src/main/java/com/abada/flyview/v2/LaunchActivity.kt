package com.abada.flyview.v2

import android.content.Intent
import android.widget.Toast
import com.abada.flyView.overlay.FlyLaunchActivity
import com.abada.flyView.overlay.FlyOverlay

/** What a widget, a tile or a notification action points to: starts the overlay from a visible context */
class LaunchActivity : FlyLaunchActivity() {
    override suspend fun onLaunch(intent: Intent) {
        BubbleEntry.request(intent.getStringExtra("text").orEmpty())
        FlyOverlay.start(this)
    }

    override fun onPermissionMissing() {
        Toast.makeText(this, "Allow drawing over other apps", Toast.LENGTH_LONG).show()
    }
}
