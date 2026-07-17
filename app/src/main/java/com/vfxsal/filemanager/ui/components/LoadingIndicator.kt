package com.vfxsal.filemanager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's shared loading spinner: a continuously rotating circle made of two arced strokes,
 * each capped with an arrowhead at its leading edge, so every loading state in the app reads
 * as a clean "circle with arrows" motion instead of the stock Material spinner's bare arc.
 */
@Composable
fun CurlyLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "circularLoading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "circularLoadingRotation",
    )

    Canvas(modifier = modifier.size(size)) {
        rotate(rotation) {
            val strokeWidth = this.size.minDimension * 0.09f
            val radius = this.size.minDimension / 2f - strokeWidth
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val sweep = 130f
            val arrowLen = strokeWidth * 2.4f
            val arrowWidth = strokeWidth * 2f

            for (startAngle in listOf(0f, 180f)) {
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                val endAngleRad = Math.toRadians((startAngle + sweep).toDouble())
                val tipX = center.x + radius * cos(endAngleRad).toFloat()
                val tipY = center.y + radius * sin(endAngleRad).toFloat()
                val tangentX = -sin(endAngleRad).toFloat()
                val tangentY = cos(endAngleRad).toFloat()
                val perpX = -tangentY
                val perpY = tangentX

                val apexX = tipX + tangentX * arrowLen * 0.6f
                val apexY = tipY + tangentY * arrowLen * 0.6f
                val baseCx = tipX - tangentX * arrowLen * 0.4f
                val baseCy = tipY - tangentY * arrowLen * 0.4f

                val arrowhead = Path().apply {
                    moveTo(apexX, apexY)
                    lineTo(baseCx + perpX * arrowWidth / 2f, baseCy + perpY * arrowWidth / 2f)
                    lineTo(baseCx - perpX * arrowWidth / 2f, baseCy - perpY * arrowWidth / 2f)
                    close()
                }
                drawPath(path = arrowhead, color = color)
            }
        }
    }
}
