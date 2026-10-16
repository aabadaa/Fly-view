package com.abada.flyView.overlay

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

/**
 * The default content of the drop area at the bottom of the screen while a window is dragged:
 * a circle with a cross that grows and turns red when the window is over it.
 * Replace it with `FlyOverlay.dismissZoneContent`.
 */
internal val DefaultDismissZoneContent: @Composable (isHovered: Boolean) -> Unit = { isHovered ->
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
