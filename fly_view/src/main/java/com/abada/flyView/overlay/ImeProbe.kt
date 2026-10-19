package com.abada.flyView.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Watches the keyboard on its own. The insets a window receives only cover the part of that window
 * the keyboard overlaps, so a small window can't tell how tall the keyboard is. This is an invisible,
 * untouchable, 1 px wide window as tall as the display: the overlap it gets is the whole keyboard.
 * It exists only while a window that avoids the keyboard is shown.
 */
internal object ImeProbe {
    /** The top of the keyboard in screen coordinates, null while it is closed */
    var top: Int? = null
        private set

    private var view: View? = null
    private var windowManager: WindowManager? = null
    private val listeners = LinkedHashSet<(Int?) -> Unit>()

    /** Starts watching for [listener], the first one adds the probe window */
    fun acquire(context: Context, windowManager: WindowManager, listener: (Int?) -> Unit) {
        listeners += listener
        if (view != null) {
            listener(top)
            return
        }
        val probe = View(context)
        ViewCompat.setOnApplyWindowInsetsListener(probe) { v, insets ->
            val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val overlap = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val location = IntArray(2)
            v.getLocationOnScreen(location)
            val newTop = if (visible && overlap > 0) location[1] + v.height - overlap else null
            if (newTop != top) {
                top = newTop
                listeners.toList().forEach { it(newTop) }
            }
            insets
        }
        val params = WindowManager.LayoutParams(
            1,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        windowManager.addView(probe, params)
        view = probe
        this.windowManager = windowManager
    }

    /** Stops watching for [listener], the last one removes the probe window */
    fun release(listener: (Int?) -> Unit) {
        listeners -= listener
        if (listeners.isNotEmpty()) return
        val probe = view ?: return
        view = null
        top = null
        try {
            windowManager?.removeView(probe)
        } catch (e: IllegalArgumentException) {
            // already gone
        }
        windowManager = null
    }
}
