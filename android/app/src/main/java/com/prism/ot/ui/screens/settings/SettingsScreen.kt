package com.prism.ot.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.ot.data.model.TransferMode
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.ml.model.ModelStatus
import com.prism.ot.ui.components.PrismConfirmationDialog
import com.prism.ot.ui.components.PrismTopBar
import com.prism.ot.ui.components.formatBytes
import com.prism.ot.ui.theme.PrismColors

/**
 * Settings screen.
 *
 * Grouped into sections: scanning defaults, feedback, advanced tuning,
 * model status, and storage. Every control writes directly through the
 * ViewModel to `PrismPreferences`; there is no local draft state, so a
 * setting change is durable the moment it is made.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = PrismColors.Background,
        topBar = { PrismTopBar(title = "Settings", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { SectionHeader("Scanning") }

            item {
                ModeSelector(
                    current = uiState.preferredMode,
                    onSelect = viewModel::setPreferredMode,
                )
            }

            item {
                SwitchRow(
                    title = "Auto-detect mode",
                    subtitle = "Choose static or kinetic from the scene",
                    checked = uiState.autoDetectMode,
                    onCheckedChange = viewModel::setAutoDetectMode,
                )
            }

            item { SectionHeader("Feedback") }

            item {
                SwitchRow(
                    title = "Haptic feedback",
                    subtitle = "Vibrate on tile and face decode",
                    checked = uiState.hapticEnabled,
                    onCheckedChange = viewModel::setHapticEnabled,
                )
            }

            item {
                SwitchRow(
                    title = "Sound feedback",
                    subtitle = "Short tone on completion",
                    checked = uiState.soundEnabled,
                    onCheckedChange = viewModel::setSoundEnabled,
                )
            }

            item {
                SwitchRow(
                    title = "Keep screen on",
                    subtitle = "Prevent sleep while scanning",
                    checked = uiState.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
            }

            item { SectionHeader("Tuning") }

            item {
                SliderRow(
                    title = "Face payload",
                    valueLabel = formatBytes(uiState.faceBytes.toLong()),
                    value = uiState.faceBytes.toFloat(),
                    range = 4_096f..24_576f,
                    steps = 8,
                    onValueChange = { viewModel.setFaceBytes(it.toInt()) },
                )
            }

            item {
                SliderRow(
                    title = "Faces per mass",
                    valueLabel = "${uiState.facesPerMass}",
                    value = uiState.facesPerMass.toFloat(),
                    range = 8f..32f,
                    steps = 23,
                    onValueChange = { viewModel.setFacesPerMass(it.toInt()) },
                )
            }

            item {
                SliderRow(
                    title = "Static DPI",
                    valueLabel = "${uiState.staticDpi}",
                    value = uiState.staticDpi.toFloat(),
                    range = 300f..1200f,
                    steps = 8,
                    onValueChange = { viewModel.setStaticDpi(it.toInt()) },
                )
            }

            item {
                SliderRow(
                    title = "Rotation",
                    valueLabel = "%.2f Hz".format(uiState.rotationHz),
                    value = uiState.rotationHz,
                    range = 0.05f..1.0f,
                    steps = 18,
                    onValueChange = viewModel::setRotationHz,
                )
            }

            item {
                SwitchRow(
                    title = "Debug overlay",
                    subtitle = "Show channel state and model timings",
                    checked = uiState.debugOverlay,
                    onCheckedChange = viewModel::setDebugOverlay,
                )
            }

            item { SectionHeader("AI models") }

            items(MlModelId.entries.toList(), key = { it.name }) { id ->
                ModelStatusRow(id = id, status = uiState.modelStatuses[id] ?: ModelStatus.NotLoaded)
            }

            item { SectionHeader("Storage") }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Transferred",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrismColors.TextSecondary,
                    )
                    Text(
                        text = formatBytes(uiState.totalBytesTransferred),
                        style = MaterialTheme.typography.bodySmall,
                        color = PrismColors.TextPrimary,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Sessions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrismColors.TextSecondary,
                    )
                    Text(
                        text = uiState.totalSessionsCompleted.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = PrismColors.TextPrimary,
                    )
                }
            }

            item {
                TextButton(onClick = viewModel::requestClearData) {
                    Text("Clear statistics and preferences", color = PrismColors.Error)
                }
            }
        }
    }

    if (uiState.showClearDataConfirmation) {
        PrismConfirmationDialog(
            title = "Clear all data?",
            message = "Statistics and preferences will be reset to defaults. Received files are not deleted.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = viewModel::confirmClearData,
            onDismiss = viewModel::dismissClearData,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = PrismColors.Cyan,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            color = PrismColors.Outline,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = PrismColors.TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = PrismColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = PrismColors.Background,
                checkedTrackColor = PrismColors.Cyan,
                uncheckedThumbColor = PrismColors.TextSecondary,
                uncheckedTrackColor = PrismColors.SurfaceElevated,
            ),
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = PrismColors.TextPrimary)
            Text(text = valueLabel, style = MaterialTheme.typography.bodySmall, color = PrismColors.Cyan)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = PrismColors.Cyan,
                activeTrackColor = PrismColors.Cyan,
                inactiveTrackColor = PrismColors.Outline,
            ),
        )
    }
}

@Composable
private fun ModeSelector(
    current: TransferMode,
    onSelect: (TransferMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransferMode.entries.forEach { mode ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (mode == current) PrismColors.Cyan else PrismColors.SurfaceElevated,
                onClick = { onSelect(mode) },
            ) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (mode == current) PrismColors.Background else PrismColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelStatusRow(id: MlModelId, status: ModelStatus) {
    val (label, color) = when (status) {
        is ModelStatus.Loaded -> "${status.accelerator.displayName}" to PrismColors.Success
        is ModelStatus.Failed -> "failed" to PrismColors.Error
        ModelStatus.NotLoaded -> "not loaded" to PrismColors.TextDisabled
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = id.displayName, style = MaterialTheme.typography.bodyMedium, color = PrismColors.TextPrimary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
