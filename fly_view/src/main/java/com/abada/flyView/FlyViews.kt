package com.abada.flyView

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.abada.flyView.windowManagerUtils.addFlyInfo
import com.abada.flyView.windowManagerUtils.showedViews
import com.abada.flyView.windowManagerUtils.updateFlyView
import java.util.UUID

/**
 * The single entry point of the library. Everything is main thread only.
 *
 * ```
 * FlyViews.register("player") { FlyViewInfo(controller = PlayerController()) { PlayerView() } }
 * FlyViews.show(context, "player")                       // directly in the window manager
 * FlyViews.show(context, "player", MyService::class.java) // inside a foreground service
 * FlyViews.hide("player")
 * ```
 */
object FlyViews {
    private const val TAG = "FlyViews"
    internal const val EXTRA_KEY = "com.abada.flyView.KEY"

    /**
     * The providers of the views by key, add yours with [register]. A provider is called
     * every time the view is shown, so every time you get a fresh [FlyViewInfo].
     */
    val infoProviders: MutableMap<String, () -> FlyViewInfo<out FlyController>> = mutableMapOf()

    /** Defines the [FlyViewInfo] that [show] will show for [key] */
    fun register(key: String, provider: () -> FlyViewInfo<out FlyController>) {
        infoProviders[key] = provider
    }

    /**
     * Shows the view that is registered for [key].
     *
     * If the overlay permission is missing the settings screen is opened and `false` is returned,
     * use [requestOverlayPermission] with an activity to know when the user is back.
     * If the view is already showing it is not added twice, only [bundle] is delivered to it.
     *
     * @param context Any context
     * @param key The key you passed to [register]
     * @param serviceClass Pass your [FlyViewService] subclass to keep the view alive when your
     * app is in the background, `null` to add it straight to the window manager
     * @param bundle An optional bundle that will be passed to [FlyController.update]
     * @return `true` if the view is showing (or the service is starting), `false` otherwise, for example
     * when the system refuses to start the service from the background (the error is logged)
     */
    fun show(
        context: Context,
        key: String,
        serviceClass: Class<out FlyViewService>? = null,
        bundle: Bundle? = null,
    ): Boolean {
        if (!hasOverlayPermission(context)) {
            requestOverlayPermission(context)
            return false
        }
        if (infoProviders[key] == null) {
            Log.e(TAG, "show: nothing is registered for \"$key\", call FlyViews.register first")
            return false
        }
        if (serviceClass != null) {
            val intent = Intent(context, serviceClass)
            bundle?.let { intent.putExtras(it) }
            intent.putExtra(EXTRA_KEY, key)
            return try {
                context.startForegroundService(intent)
                true
            } catch (e: IllegalStateException) {
                // Android 12+ refuses to start a foreground service from the background
                Log.e(TAG, "show: the system refused to start ${serviceClass.simpleName}", e)
                false
            } catch (e: SecurityException) {
                Log.e(TAG, "show: ${serviceClass.simpleName} is missing a manifest declaration or permission", e)
                false
            }
        }
        if (!isShown(key)) {
            val app = context.applicationContext
            val windowManager = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (!windowManager.addFlyInfo(app, key, infoProviders.getValue(key)())) return false
        }
        bundle?.let { update(key, it) }
        return true
    }

    /** Removes the view with its exit animation. Returns `false` if it is not showing. */
    fun hide(key: String): Boolean {
        val info = showedViews[key] ?: return false
        info.removeView()
        return true
    }

    /** Whether the view of [key] is showing now */
    fun isShown(key: String): Boolean = showedViews.containsKey(key)

    /** Calls [FlyController.update] of the showing view's controller. Returns `false` if not showing. */
    fun update(key: String, bundle: Bundle): Boolean = updateFlyView(key, bundle)

    /**
     * Gives you the controller of a showing view with its own type, without bundles.
     *
     * ```
     * FlyViews.withController<PlayerController>("player") { it.play() }
     * ```
     *
     * @return `false` if the view is not showing or its controller is not a [C]
     */
    inline fun <reified C : FlyController> withController(key: String, block: (C) -> Unit): Boolean {
        val controller = controllerOf(key) as? C ?: return false
        block(controller)
        return true
    }

    @PublishedApi
    internal fun controllerOf(key: String): FlyController? = showedViews[key]?.controller

    // permission

    /** Whether the user allowed this app to draw over other apps */
    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Opens the "draw over other apps" settings screen of this app. */
    fun requestOverlayPermission(context: Context) {
        context.startActivity(overlaySettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Opens the "draw over other apps" settings screen of this app and tells you what the user decided.
     * [onResult] is called right away with `true` if the permission is already granted.
     */
    fun requestOverlayPermission(activity: ComponentActivity, onResult: (granted: Boolean) -> Unit) {
        if (hasOverlayPermission(activity)) {
            onResult(true)
            return
        }
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = activity.activityResultRegistry.register(
            "flyView-overlay-permission-${UUID.randomUUID()}",
            ActivityResultContracts.StartActivityForResult()
        ) {
            launcher?.unregister()
            onResult(hasOverlayPermission(activity))
        }
        launcher.launch(overlaySettingsIntent(activity))
    }

    private fun overlaySettingsIntent(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
}
