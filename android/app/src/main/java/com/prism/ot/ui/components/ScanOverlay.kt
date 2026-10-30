package com.prism.ot.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.prism.ot.ui.theme.PrismColors

/**
 * Full-screen scan overlay drawn on a Compose [Canvas].
 *
 * The overlay has two layers:
 *  1. A slowly oscillating horizontal scan line, animated with
 *     `rememberInfiniteTransition` and a linear repeatable tween.
 *  2. Four corner brackets that frame the capture region.
 *
 * Drawing is confined to a single `Canvas` so the overlay costs one draw
 * pass. For custom backgrounds the recommended idiom is `drawBehind`;
 * here a dedicated `Canvas` is used because the overlay must sit above
 * the `AndroidView` that hosts the CameraX preview and must not be clipped
 * by the preview's own composition.
 */
@Composable
fun ScanOverlay(
    modifier: Modifier = Modifier,
    showCornerBrackets: Boolean = true,
    showScanLine: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "scan-line")
    val lineFraction by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan-line-position",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = 2.dp.toPx()
        val bracketLength = 36.dp.toPx()
        val inset = 28.dp.toPx()
        val cornerColor = PrismColors.ScanCorner

        if (showCornerBrackets) {
            // Top-left
            drawPath(
                path = Path().apply {
                    moveTo(inset, inset + bracketLength)
                    lineTo(inset, inset)
                    lineTo(inset + bracketLength, inset)
                },
                color = cornerColor,
                style = Stroke(width = stroke),
            )
            // Top-right
            drawPath(
                path = Path().apply {
                    moveTo(size.width - inset - bracketLength, inset)
                    lineTo(size.width - inset, inset)
                    lineTo(size.width - inset, inset + bracketLength)
                },
                color = cornerColor,
                style = Stroke(width = stroke),
            )
            // Bottom-left
            drawPath(
                path = Path().apply {
                    moveTo(inset, size.height - inset - bracketLength)
                    lineTo(inset, size.height - inset)
                    lineTo(inset + bracketLength, size.height - inset)
                },
                color = cornerColor,
                style = Stroke(width = stroke),
            )
            // Bottom-right
            drawPath(
                path = Path().apply {
                    moveTo(size.width - inset - bracketLength, size.height - inset)
                    lineTo(size.width - inset, size.height - inset)
                    lineTo(size.width - inset, size.height - inset - bracketLength)
                },
                color = cornerColor,
                style = Stroke(width = stroke),
            )
        }

        if (showScanLine) {
            val y = size.height * lineFraction
            drawLine(
                color = PrismColors.ScanLine,
                start = Offset(inset, y),
                end = Offset(size.width - inset, y),
                strokeWidth = stroke,
            )
        }
    }
}
