package com.prism.ot.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.prism.ot.data.model.TransferProgress
import com.prism.ot.ui.theme.PrismColors
import java.util.Locale

/**
 * Transfer progress block: animated fraction bar, byte counts, throughput,
 * packets collected, and ETA. The bar uses `animateFloatAsState` rather
 * than a raw value so updates that arrive in bursts (the progress emitter
 * throttles at 150 ms) do not visibly snap.
 */
@Composable
fun TransferProgressBlock(
    progress: TransferProgress,
    modifier: Modifier = Modifier,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fractionComplete,
        animationSpec = tween(durationMillis = 220),
        label = "transfer-progress",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${(animatedFraction * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = PrismColors.Cyan,
            )
            Text(
                text = "${formatBytes(progress.bytesReceived)} / ${formatBytes(progress.bytesTotal)}",
                style = MaterialTheme.typography.bodySmall,
                color = PrismColors.TextSecondary,
            )
        }

        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
            color = PrismColors.Cyan,
            trackColor = PrismColors.Outline,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "▲ ${formatBps(progress.currentThroughputBps)}",
                style = MaterialTheme.typography.labelSmall,
                color = PrismColors.TextSecondary,
            )
            Text(
                text = "ETA ${formatEta(progress.etaMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = PrismColors.TextSecondary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "packets ${progress.packetsCollected}/${progress.packetsRequired}",
                style = MaterialTheme.typography.labelSmall,
                color = PrismColors.TextDisabled,
            )
            Text(
                text = "frames ${progress.framesDecoded}/${progress.framesProcessed}",
                style = MaterialTheme.typography.labelSmall,
                color = PrismColors.TextDisabled,
            )
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

internal fun formatBps(bps: Long): String = "${formatBytes(bps)}/s"

internal fun formatEta(millis: Long): String {
    if (millis <= 0L) return "--"
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
