package com.abada.flyView.overlay

import android.app.ActivityManager
import android.app.Application
import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.abada.flyView.FlyView
import com.abada.flyView.core.ClosingPolicy
import com.abada.flyView.core.ClosingReason
import com.abada.flyView.core.DismissCoordinator
import com.abada.flyView.core.DismissReason
import com.abada.flyView.core.KeyedSlots
import com.abada.flyView.windowManagerUtils.DismissOverlay

/** `true` inside the blocks of `FlyOverlay.contribute`, which live in the process and not in an activity */
internal val LocalFlyHostBlock = staticCompositionLocalOf { false }

/**
 * The process level part of the overlay: the host window that composes the `contribute` blocks,
 * the registry of windows and items, and what the visibility rules need (foreground, closing hints).
 * Main thread only.
 */
internal object FlyOverlayHost {
    private const val TAG = "FlyOverlay"
    private const val IDLE_START_GRACE_MS = 5_000L
    private const val BACKGROUND_DEBOUNCE_MS = 700L

    private val handler = Handler(Looper.getMainLooper())

    private var app: Application? = null
    private var hostView: FlyView? = null
    private var windowManager: WindowManager? = null

    var isRunning by mutableStateOf(false)
        private set

    /** `true` while an activity of the app is started. Debounced so a rotation does not count as background. */
    var foreground by mutableStateOf(true)
        private set

    var closingReason by mutableStateOf<ClosingReason?>(null)
        private set

    var serviceClass: Class<out FlyService>? = null
    var stopWhenEmpty = true
    private var service: FlyService? = null
    val serviceActive: Boolean get() = service != null

    // contributions: blocks composed by the host
    private val contributions = mutableStateListOf<Pair<String, @Composable () -> Unit>>()
    private val contributionTokens = KeyedSlots<String, Unit>()

    // windows and items
    private val windows = KeyedSlots<String, FlyWindowRuntime>()
    private val itemSlots = HashMap<String, KeyedSlots<String, FlyItemEntry>>()
    private val items = mutableStateMapOf<String, List<FlyItemEntry>>()
    private val states = HashMap<String, FlyWindowState>()
    private var hadWanted = false

    private var startedActivities = 0
    private val toBackground = Runnable { foreground = false }

    // state

    fun stateFor(context: Context, key: String, initial: FlyPosition, persist: Boolean): FlyWindowState {
        val state = states.getOrPut(key) { FlyWindowState(key, initial) }
        if (persist && state.persistContext == null) state.load(context)
        return state
    }

    // start / stop

    fun start(context: Context) {
        val application = context.applicationContext as Application
        if (isRunning) return
        app = application
        val wm = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        foreground = isProcessForeground(application)
        startedActivities = 0
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        application.registerComponentCallbacks(componentCallbacks)

        val view = FlyView(application, content = { HostContent() })
        wm.addView(
            view,
            WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSPARENT
            ).also { it.gravity = Gravity.TOP or Gravity.START }
        )
        hostView = view
        hadWanted = false
        isRunning = true
        handler.postDelayed({
            if (isRunning && !hadWanted && stopWhenEmpty) stop()
        }, IDLE_START_GRACE_MS)
    }

    fun stop() {
        if (!isRunning) return
        windows.values().forEach { it.notifyClosing(ClosingReason.HostStopping) }
        isRunning = false
        windows.values().forEach { it.remove() }
        DismissOverlay.hide()
        val view = hostView
        hostView = null
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "stop: the host window was already removed")
            }
            view.dispose()
        }
        app?.unregisterActivityLifecycleCallbacks(activityCallbacks)
        app?.unregisterComponentCallbacks(componentCallbacks)
        handler.removeCallbacks(toBackground)
        closingReason = null
        service?.stopNow()
    }

    @Composable
    private fun HostContent() {
        CompositionLocalProvider(LocalFlyHostBlock provides true) {
            for ((id, block) in contributions) {
                key(id) { block() }
            }
        }
    }

    fun contribute(id: String, content: @Composable () -> Unit): Long {
        val token = contributionTokens.put(id, Unit)
        val index = contributions.indexOfFirst { it.first == id }
        if (index >= 0) contributions[index] = id to content else contributions.add(id to content)
        return token
    }

    fun removeContribution(id: String, token: Long) {
        if (!contributionTokens.remove(id, token)) return
        contributions.removeAll { it.first == id }
    }

    // windows

    fun registerWindow(key: String, runtime: FlyWindowRuntime): Long = windows.put(key, runtime)

    fun unregisterWindow(key: String, token: Long) {
        // a replaced window (same key, newer owner) leaves its successor alone
        if (!windows.remove(key, token)) return
        checkEmpty()
    }

    /** A window wants to exist, so the overlay has something to show and may stop when it is gone */
    fun markWanted() {
        hadWanted = true
    }

    /** Stops the overlay (and the service) when no window is alive any more */
    fun checkEmpty() {
        handler.post {
            if (isRunning && stopWhenEmpty && hadWanted && windows.values().none { it.isAlive }) stop()
        }
    }

    fun window(key: String): FlyWindowRuntime? = windows[key]
    fun allWindows(): List<FlyWindowRuntime> = windows.values()

    fun onWindowDismissCommitted(runtime: FlyWindowRuntime) {
        // nothing to do for the host, the window hides itself when nobody handles the request
    }

    // items

    fun registerItem(entry: FlyItemEntry): Long {
        val slots = itemSlots.getOrPut(entry.window) { KeyedSlots() }
        val token = slots.put(entry.id, entry)
        publishItems(entry.window)
        return token
    }

    fun unregisterItem(entry: FlyItemEntry, token: Long) {
        val slots = itemSlots[entry.window] ?: return
        if (slots.remove(entry.id, token)) publishItems(entry.window)
    }

    fun publishItems(window: String) {
        val slots = itemSlots[window]
        items[window] = slots?.values()?.sortedBy { it.order } ?: emptyList()
    }

    fun itemsOf(window: String): List<FlyItemEntry> = items[window] ?: emptyList()

    fun requestDismissItem(window: String, id: String): Boolean {
        val entry = itemSlots[window]?.get(id) ?: return false
        entry.coordinator.request(DismissReason.External)
        return true
    }

    fun requestDismissAll() {
        allWindows().forEach { it.coordinator.request(DismissReason.External) }
        itemSlots.values.forEach { slots -> slots.values().forEach { it.coordinator.request(DismissReason.External) } }
    }

    // service

    fun attachService(s: FlyService) {
        service = s
    }

    fun detachService(s: FlyService) {
        if (service === s) service = null
    }

    // foreground and closing hints

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
            handler.removeCallbacks(toBackground)
            foreground = true
            closingReason = null
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            if (startedActivities == 0) handler.postDelayed(toBackground, BACKGROUND_DEBOUNCE_MS)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            val reason = ClosingPolicy.reasonForTrim(level, serviceActive) ?: return
            closingReason = reason
            allWindows().forEach { it.notifyClosing(reason) }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {}

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {}
    }

    private fun isProcessForeground(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}
