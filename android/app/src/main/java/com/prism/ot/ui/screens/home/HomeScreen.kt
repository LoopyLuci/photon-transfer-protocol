package com.prism.ot.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.ot.data.model.TransferMode
import com.prism.ot.ui.components.PrismPrimaryButton
import com.prism.ot.ui.components.PrismSecondaryButton
import com.prism.ot.ui.components.PrismTopBar
import com.prism.ot.ui.components.formatBytes
import com.prism.ot.ui.theme.PrismColors

/**
 * Home screen.
 *
 * The screen is a single vertically scrolling column: header, two primary
 * scan actions, a statistics card, and a recent‑transfers list. It
 * collects [HomeViewModel.uiState] with `collectAsStateWithLifecycle()`
 * so that a backgrounded home screen does not keep the preference flows
 * hot.
 */
@Composable
fun HomeScreen(
    onScanStatic: () -> Unit,
    onScanKinetic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenResult: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = PrismColors.Background,
        topBar = {
            PrismTopBar(
                title = "PRISM",
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = PrismColors.TextPrimary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Optical data transport",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PrismColors.TextSecondary,
                )
            }

            item {
                ModelStatusCard(
                    ready = uiState.contentState.mlModelsReady,
                    missingModels = uiState.contentState.missingModels,
                )
            }

            item {
                PrismPrimaryButton(
                    title = "Static scan",
                    subtitle = "Printed mosaic · archival · up to 500 KB per page",
                    icon = Icons.Outlined.Description,
                    onClick = onScanStatic,
                )
            }

            item {
                PrismPrimaryButton(
                    title = "Kinetic scan",
                    subtitle = "Rotating mass · megabyte transfers · motor control",
                    icon = Icons.Outlined.ViewInAr,
                    onClick = onScanKinetic,
                )
            }

            item {
                StatsCard(
                    sessions = uiState.contentState.totalSessionsCompleted,
                    bytes = uiState.contentState.totalBytesTransferred,
                    preferredMode = uiState.contentState.preferredMode,
                )
            }

            if (uiState.contentState.recentResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent transfers",
                        style = MaterialTheme.typography.titleLarge,
                        color = PrismColors.TextPrimary,
                    )
                }
                items(
                    items = uiState.contentState.recentResults,
                    key = { it.sessionId },
                ) { recent ->
                    RecentTransferRow(recent = recent, onClick = { onOpenResult(recent.sessionId) })
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "PRISM format v1 · native core · all processing on device",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrismColors.TextDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ModelStatusCard(
    ready: Boolean,
    missingModels: List<String>,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (ready) PrismColors.Surface else PrismColors.SurfaceElevated,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ready) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(PrismColors.Success),
                )
                Text(
                    text = "AI models loaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrismColors.TextPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = PrismColors.Warning,
                )
                Column {
                    Text(
                        text = "Some AI models are missing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrismColors.TextPrimary,
                    )
                    Text(
                        text = missingModels.joinToString(", ").ifBlank { "unknown" },
                        style = MaterialTheme.typography.labelSmall,
                        color = PrismColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(
    sessions: Int,
    bytes: Long,
    preferredMode: TransferMode,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PrismColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            StatColumn(label = "Sessions", value = sessions.toString())
            StatColumn(label = "Transferred", value = formatBytes(bytes))
            StatColumn(label = "Default", value = preferredMode.displayName)
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PrismColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = PrismColors.Cyan,
        )
    }
}

@Composable
private fun RecentTransferRow(
    recent: RecentTransfer,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PrismColors.Surface,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = when (recent.mode) {
                    TransferMode.KINETIC -> Icons.Outlined.ViewInAr
                    TransferMode.STATIC, TransferMode.FALLBACK -> Icons.Outlined.ViewQuilt
                },
                contentDescription = null,
                tint = PrismColors.Violet,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recent.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrismColors.TextPrimary,
                )
                Text(
                    text = "${formatBytes(recent.sizeBytes)} · ${recent.mode.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrismColors.TextSecondary,
                )
            }
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = PrismColors.TextDisabled,
            )
        }
    }
}
