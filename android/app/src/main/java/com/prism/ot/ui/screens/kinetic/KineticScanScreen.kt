package com.prism.ot.ui.screens.kinetic

import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.ot.motor.DiscoveredMotor
import com.prism.ot.motor.MotorConnectionState
import com.prism.ot.session.SessionResult
import com.prism.ot.session.SessionState
import com.prism.ot.ui.components.CameraPermissionGate
import com.prism.ot.ui.components.PrismConfirmationDialog
import com.prism.ot.ui.components.PrismTopBar
import com.prism.ot.ui.components.ScanOverlay
import com.prism.ot.ui.components.TransferProgressBlock
import com.prism.ot.ui.components.formatBytes
import com.prism.ot.ui.theme.PrismColors

/**
 * Kinetic scan screen.
 *
 * Inherits the static screen's three-layer layout and adds a motor panel
 * above the progress surface. The motor panel is the only part of the UI
 * that differs structurally; everything else is shared behaviour rendered
 * from [KineticScanUiState].
 */
@Composable
fun KineticScanScreen(
    onFinished: (String?) -> Unit,
    onBack: () -> Unit,
    viewModel: KineticScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.terminalResult) {
        when (val result = uiState.terminalResult) {
            is SessionResult.Completed -> {
                viewModel.onResultConsumed()
                onFinished(result.transfer.outputUri.toString())
            }
            is SessionResult.TransferFailed -> {
                viewModel.onResultConsumed()
                onFinished(null)
            }
            is SessionResult.Cancelled -> {
                viewModel.onResultConsumed()
                onBack()
            }
            is SessionResult.Error -> viewModel.onResultConsumed()
            null -> Unit
        }
    }

    CameraPermissionGate {
        Box(modifier = Modifier.fillMaxSize().background(PrismColors.Background)) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView -> viewModel.bindCamera(lifecycleOwner, previewView) },
            )

            ScanOverlay(showScanLine = uiState.sessionState is SessionState.Running)

            Column(modifier = Modifier.fillMaxSize()) {
                PrismTopBar(
                    title = "Kinetic scan",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = viewModel::toggleTorch) {
                            Icon(
                                imageVector = if (uiState.torchEnabled) {
                                    Icons.Default.FlashOn
                                } else {
                                    Icons.Default.FlashOff
                                },
                                contentDescription = "Toggle flashlight",
                                tint = if (uiState.torchEnabled) {
                                    PrismColors.Amber
                                } else {
                                    PrismColors.TextPrimary
                                },
                            )
                        }
                    },
                )

                Spacer(Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = PrismColors.Surface.copy(alpha = 0.94f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        MotorPanel(
                            state = uiState.motorState,
                            commandedHz = uiState.commandedRotationHz,
                            onConnect = viewModel::startMotorDiscovery,
                            onDisconnect = viewModel::disconnectMotor,
                        )

                        if (uiState.facesDetected > 0) {
                            FaceCounter(
                                detected = uiState.facesDetected,
                                decoded = uiState.facesDecoded,
                            )
                        }

                        uiState.progress?.let { progress ->
                            TransferProgressBlock(progress = progress)
                        } ?: Text(
                            text = "Waiting for the first face…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrismColors.TextSecondary,
                        )
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                ErrorBanner(
                    message = message,
                    onDismiss = viewModel::onResultConsumed,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 96.dp),
                )
            }
        }
    }

    if (uiState.showMotorPicker) {
        MotorPickerDialog(
            motors = uiState.availableMotors,
            isScanning = uiState.isScanningForMotors,
            onSelect = viewModel::connectMotor,
            onDismiss = viewModel::dismissMotorPicker,
        )
    }

    if (uiState.showCancelConfirmation) {
        PrismConfirmationDialog(
            title = "Stop kinetic transfer?",
            message = "Collected faces will be discarded. The mass will keep its current rotation.",
            confirmLabel = "Stop",
            destructive = true,
            onConfirm = viewModel::confirmCancel,
            onDismiss = viewModel::dismissCancel,
        )
    }
}

@Composable
private fun MotorPanel(
    state: MotorConnectionState,
    commandedHz: Float,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PrismColors.SurfaceElevated,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val (icon, tint, label) = when (state) {
                is MotorConnectionState.Ready ->
                    Triple(Icons.Default.BluetoothConnected, PrismColors.Success, "Motor connected")
                is MotorConnectionState.Connecting,
                is MotorConnectionState.DiscoveringServices,
                is MotorConnectionState.Reconnecting ->
                    Triple(Icons.Default.Bluetooth, PrismColors.Amber, "Connecting…")
                is MotorConnectionState.Disconnected,
                is MotorConnectionState.Failed ->
                    Triple(Icons.Default.BluetoothDisabled, PrismColors.TextSecondary, "No motor")
                else ->
                    Triple(Icons.Default.BluetoothDisabled, PrismColors.TextSecondary, "No motor")
            }

            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, color = PrismColors.TextPrimary)
                if (state is MotorConnectionState.Ready && commandedHz > 0f) {
                    Text(
                        text = "%.3f Hz · face-rate matched".format(commandedHz),
                        style = MaterialTheme.typography.labelSmall,
                        color = PrismColors.TextSecondary,
                    )
                }
            }

            if (state is MotorConnectionState.Ready) {
                androidx.compose.material3.TextButton(onClick = onDisconnect) {
                    Text("Disconnect", color = PrismColors.TextSecondary)
                }
            } else {
                androidx.compose.material3.TextButton(onClick = onConnect) {
                    Text("Connect", color = PrismColors.Cyan)
                }
            }
        }
    }
}

@Composable
private fun FaceCounter(detected: Int, decoded: Int) {
    val fraction = if (detected == 0) 0f else decoded.toFloat() / detected.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Face decode rate",
                style = MaterialTheme.typography.labelSmall,
                color = PrismColors.TextSecondary,
            )
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (fraction > 0.7f) PrismColors.Success else PrismColors.Warning,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = if (fraction > 0.7f) PrismColors.Success else PrismColors.Warning,
            trackColor = PrismColors.Outline,
        )
        Text(
            text = "$decoded of $detected faces decoded",
            style = MaterialTheme.typography.labelSmall,
            color = PrismColors.TextDisabled,
        )
    }
}

@Composable
private fun MotorPickerDialog(
    motors: List<DiscoveredMotor>,
    isScanning: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a rotating mass", style = MaterialTheme.typography.headlineMedium) },
        text = {
            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = PrismColors.Cyan)
                    Text("Scanning for PRISM motors…", color = PrismColors.TextSecondary)
                }
            } else if (motors.isEmpty()) {
                Text("No motors found. Make sure the mass is powered on.", color = PrismColors.TextSecondary)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(motors, key = { it.macAddress }) { motor ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PrismColors.SurfaceElevated,
                            onClick = { onSelect(motor.macAddress) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = motor.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PrismColors.TextPrimary,
                                    )
                                    Text(
                                        text = motor.macAddress,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PrismColors.TextSecondary,
                                    )
                                }
                                Text(
                                    text = "${motor.rssi} dBm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrismColors.TextDisabled,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close", color = PrismColors.TextSecondary)
            }
        },
        containerColor = PrismColors.Surface,
    )
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PrismColors.Error.copy(alpha = 0.92f),
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = PrismColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = PrismColors.TextPrimary)
            }
        }
    }
}
