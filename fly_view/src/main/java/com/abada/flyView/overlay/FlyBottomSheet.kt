package com.abada.flyView.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Defaults of [FlyBottomSheet] */
object FlyBottomSheetDefaults {
    val Shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val ScrimColor = Color(0x66000000)

    @Composable
    fun containerColor(): Color = if (isSystemInDarkTheme()) Color(0xFF211F26) else Color(0xFFF7F2FA)
}

/**
 * A modal sheet from the bottom of the screen that works inside a [FlyWindow]. Compose it while it is
 * open; leaving the composition closes it (there is no exit animation).
 *
 * It is a touchable, focusable [FlyOverlayLayer] with a scrim: a touch on the scrim calls
 * [onDismissRequest], and so does the back button. It takes the focus, so text fields in [content]
 * work, and the sheet stays above the keyboard.
 *
 * @param onDismissRequest the user touched the scrim or pressed back: update your state
 * @param containerColor the color of the sheet
 */
@Composable
fun FlyBottomSheet(
    onDismissRequest: () -> Unit,
    shape: Shape = FlyBottomSheetDefaults.Shape,
    containerColor: Color = FlyBottomSheetDefaults.containerColor(),
    scrimColor: Color = FlyBottomSheetDefaults.ScrimColor,
    content: @Composable () -> Unit,
) {
    FlyOverlayLayer(touchable = true, focusable = true, onBackPress = onDismissRequest) {
        val shown = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown.value = true }
        AnimatedVisibility(visible = shown.value, enter = fadeIn()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AnimatedVisibility(visible = shown.value, enter = slideInVertically { it }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // a touch on the sheet does not reach the scrim
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                            .background(containerColor, shape)
                            .navigationBarsPadding()
                            .imePadding()
                    ) { content() }
                }
            }
        }
    }
}
