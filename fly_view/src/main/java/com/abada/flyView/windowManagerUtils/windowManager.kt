package com.abada.flyView.windowManagerUtils

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.FlyViews

/**
 * Define a FlyViewInfo and call this method to add it to the WindowManager.
 * Prefer [FlyViews.show] which also takes care of the overlay permission and the service.
 *
 * If the overlay permission is not granted the settings screen is opened and nothing is added.
 *
 * @param context Any context to create a [android.view.View] object
 * @param key A string to identify your view, used when you want to update it using [updateFlyView] method or remove it using [removeFlyView]
 * @param flyViewInfo The info of your fly view that holds your view and your controller
 * @param onRemoved Called once the view is removed
 * @return `true` if the view is showing, `false` if the permission is missing
 */
fun <T : FlyController> WindowManager.addFlyInfo(
    context: Context,
    key: String,
    flyViewInfo: FlyViewInfo<T>,
    onRemoved: () -> Unit = {},
): Boolean {
    if (!FlyViews.hasOverlayPermission(context)) {
        FlyViews.requestOverlayPermission(context)
        return false
    }
    if (showedViews.containsKey(key)) return true
    showedViews[key] = flyViewInfo
    try {
        flyViewInfo.addToWindowManager(context, key, this, onRemoved)
    } catch (e: Exception) {
        showedViews.remove(key)
        throw e
    }
    return true
}

/**
 * Call this method to remove views those are added by `addFlyInfo`, immediately and without
 * calling `onRemove`. To remove a view with its exit animation call [FlyViewInfo.removeView].
 *
 * @param key The identifier of your fly view
 */
fun WindowManager.removeFlyView(key: String) {
    showedViews.remove(key)?.dispose()
    if (showedViews.isEmpty()) DismissOverlay.hide()
}

/**
 * Call this method to pass data to your showed fly views by passing it in a bundle object.
 * Calls [FlyController.update] of the specified FlyView's controller.
 *
 * @param key The identifier of your fly view
 * @param bundle The data bundle to pass to the FlyView's controller
 * @return true if the view was found and updated, false otherwise
 */
fun updateFlyView(key: String, bundle: Bundle): Boolean {
    val info = showedViews[key] ?: return false
    info.controller.update(bundle)
    return true
}

/**
 * This view is showed at the bottom of the screen while the user drags a
 * [com.abada.flyView.FlyViewInfo] that has a dismiss zone, releasing it there removes it.
 *
 * Assign your new content to fit your app style. It receives `isHovered` which is `true`
 * when the dragged view is over the zone, use it to react (scale, color, ...).
 * The default implementation shows a circle with a cross that grows and turns red.
 */
var closeViewContent: @Composable (isHovered: Boolean) -> Unit = { isHovered ->
    val scale by animateFloatAsState(if (isHovered) 1.3f else 1f, label = "flyViewDismissScale")
    val color by animateColorAsState(
        if (isHovered) Color(0xFFE53935) else Color(0xB3212121), label = "flyViewDismissColor"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)))),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
                .background(color, CircleShape)
                .semantics { contentDescription = "Drop here to close" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val stroke = 3.dp.toPx()
                drawLine(
                    Color.White, Offset.Zero, Offset(size.width, size.height),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
                drawLine(
                    Color.White, Offset(size.width, 0f), Offset(0f, size.height),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
            }
        }
    }
}
