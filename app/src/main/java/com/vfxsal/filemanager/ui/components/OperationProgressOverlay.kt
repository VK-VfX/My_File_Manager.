package com.vfxsal.filemanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vfxsal.filemanager.util.OperationProgressBus

/**
 * Renders the [OperationProgressBus] state as a non-dismissable dialog with a gradient
 * progress bar and the app's circular spinner. Placed once at the app root so any batch
 * operation, from any tab, shows the same overlay.
 */
@Composable
fun OperationProgressOverlay() {
    val progress by OperationProgressBus.state.collectAsStateWithLifecycle()
    val current = progress ?: return

    val fraction by animateFloatAsState(
        targetValue = if (current.total == 0) 0f else current.done.toFloat() / current.total,
        animationSpec = tween(durationMillis = 200),
        label = "operationProgressFraction",
    )

    Dialog(
        onDismissRequest = { /* deliberately not dismissable while the operation runs */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp).widthIn(min = 260.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CurlyLoadingIndicator(size = 28.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(current.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${current.done} of ${current.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                GradientProgressBar(
                    fraction = fraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun GradientProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val startColor = MaterialTheme.colorScheme.primary
    val endColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = corner)
        val filledWidth = size.width * fraction.coerceIn(0f, 1f)
        if (filledWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(startColor, endColor)),
                size = Size(filledWidth, size.height),
                cornerRadius = corner,
            )
        }
    }
}
