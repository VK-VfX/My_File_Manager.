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
import androidx.compose.ui.graphics.Brush
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
 * The app's shared loading spinner: a rotating wavy/curly ring drawn on [Canvas] rather than
 * the stock Material [androidx.compose.material3.CircularProgressIndicator]'s plain arc, so
 * every loading state in the app reads as one consistent, centered, "materialistic curly
 * lines" animation instead of a bare spinner tucked in a corner.
 */
@Composable
fun CurlyLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "curlyLoading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "curlyLoadingRotation",
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "curlyLoadingWave",
    )

    Canvas(modifier = modifier.size(size)) {
        rotate(rotation) {
            val baseRadius = this.size.minDimension / 2f * 0.6f
            val amplitude = this.size.minDimension / 2f * 0.24f
            val waveCount = 5
            val steps = 120
            val path = Path()
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val angle = t * 2f * Math.PI.toFloat()
                val r = baseRadius + amplitude * sin(waveCount * angle + wavePhase)
                val x = this.size.width / 2f + r * cos(angle)
                val y = this.size.height / 2f + r * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(
                path = path,
                brush = Brush.sweepGradient(
                    listOf(
                        color.copy(alpha = 0.2f),
                        color,
                        color.copy(alpha = 0.55f),
                        color,
                        color.copy(alpha = 0.2f),
                    ),
                ),
                style = Stroke(width = this.size.minDimension * 0.075f, cap = StrokeCap.Round),
            )
        }
    }
}
