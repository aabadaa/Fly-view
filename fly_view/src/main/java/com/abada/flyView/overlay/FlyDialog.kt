package com.abada.flyView.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.abada.flyView.FlyView
import com.abada.flyView.core.FlyOverlayNotStartedException

/**
 * A modal dialog on a window of our own. Android's `Dialog` needs an activity window token and
 * fails with the application context an overlay lives in, this one does not.
 *
 * It covers the screen with a scrim, takes the focus, and calls [onDismissRequest] for the back
 * button and for a tap outside of [content]. It is composed in the caller's composition, and removed
 * when it leaves it. It is added after the windows that exist, so it is on top of them.
 *
 * The overlay has to be started before, otherwise [FlyOverlayNotStartedException] is thrown.
 */
@Composable
fun FlyDialog(
    onDismissRequest: () -> Unit,
    properties: FlyDialogProperties = FlyDialogProperties(),
    content: @Composable () -> Unit,
) {
    remember { if (!FlyOverlayHost.isRunning) throw FlyOverlayNotStartedException() }
    val appContext = LocalContext.current.applicationContext
    val parent = rememberCompositionContext()
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val currentProperties by rememberUpdatedState(properties)
    val transition = remember { MutableTransitionState(false) }

    DisposableEffect(Unit) {
        val windowManager = appContext.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        val view = FlyView(
            context = appContext,
            parentContext = parent,
            onBackFallback = { if (currentProperties.dismissOnBackPress) currentDismiss() },
        ) {
            AnimatedVisibility(visibleState = transition, enter = currentProperties.enter) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(currentProperties.scrimColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (currentProperties.dismissOnClickOutside) currentDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    // a tap on the content does not reach the scrim
                    Box(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                    ) { content() }
                }
            }
        }
        windowManager.addView(
            view,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).also { it.gravity = Gravity.TOP or Gravity.START }
        )
        onDispose {
            try {
                windowManager.removeView(view)
            } catch (e: IllegalArgumentException) {
                // already gone
            }
            view.dispose()
        }
    }
    SideEffect { transition.targetState = true }
}
