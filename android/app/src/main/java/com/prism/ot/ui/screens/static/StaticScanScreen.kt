package com.prism.ot.ui.screens.static

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.ot.session.SessionResult
import com.prism.ot.session.SessionState
import com.prism.ot.ui.components.CameraPermissionGate
import com.prism.ot.ui.components.PrismConfirmationDialog
import com.prism.ot.ui.components.PrismTopBar
import com.prism.ot.ui.components.ScanOverlay
import com.prism.ot.ui.components.TransferProgressBlock
import com.prism.ot.ui.theme.PrismColors

/**
 * Static scan screen.
 *
 * Layout is a `Box` with three layers:
 *  1. The CameraX `PreviewView`, hosted via `AndroidView`.
 *  2. The [ScanOverlay] drawn on a Compose `Canvas`.
 *  3. The control and progress surfaces, aligned to top and bottom.
 *
 * The camera permission gate wraps the entire content. Hiding the main UI
 * behind the gate avoids the "permission spaghetti" that results from
 * handling denial deep inside a camera screen.
 */
@Composable
fun StaticScanScreen(
    onFinished: (String?) -> Unit,
    onBack: () -> Unit,
    viewModel: StaticScanViewModel = hiltViewModel(),
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
                update = { previewView ->
                    // Bind once; `bindCamera` is idempotent and the
                    // PreviewView instance is stable across recompositions.
                    viewModel.bindCamera(lifecycleOwner, previewView)
                },
            )

            ScanOverlay(
                showScanLine = uiState.sessionState is SessionState.Running,
            )

            Column(modifier = Modifier.fillMaxSize()) {
                PrismTopBar(
                    title = "Static scan",
                    onBack = onBack,
                    actions = {
                        TorchAction(
                            enabled = uiState.torchEnabled,
                            onToggle = {
                                viewModel.toggleTorch()
                                // The controller is reached through the
                                // session; see the note below.
                            },
                        )
                    },
                )

                Spacer(Modifier.weight(1f))

                uiState.progress?.let { progress ->
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = PrismColors.Surface.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TransferProgressBlock(progress = progress)
                            SessionHint(state = uiState.sessionState)
                        }
                    }
                } ?: run {
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = PrismColors.Surface.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            SessionHint(state = uiState.sessionState)
                        }
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                ErrorBanner(
                    message = message,
                    onDismiss = { viewModel.onResultConsumed() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 96.dp),
                )
            }
        }
    }

    if (uiState.showCancelConfirmation) {
        PrismConfirmationDialog(
            title = "Stop scanning?",
            message = "Collected packets will be discarded. You can start again from the same page.",
            confirmLabel = "Stop",
            destructive = true,
            onConfirm = viewModel::confirmCancel,
            onDismiss = viewModel::dismissCancel,
        )
    }

    DisposableEffect(Unit) {
        onDispose { /* ViewModel.onCleared stops the session. */ }
    }
}

@Composable
private fun TorchAction(enabled: Boolean, onToggle: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onToggle) {
        androidx.compose.material3.Icon(
            imageVector = if (enabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription = if (enabled) "Turn flashlight off" else "Turn flashlight on",
            tint = if (enabled) PrismColors.Amber else PrismColors.TextPrimary,
        )
    }
}

@Composable
private fun SessionHint(state: SessionState) {
    val (title, detail) = when (state) {
        SessionState.Idle, SessionState.Preparing ->
            "Starting camera" to "Hold the page steady inside the frame."
        is SessionState.Running -> {
            val sub = state.subState
            if (sub is com.prism.ot.session.RunningSubState.StaticScanning) {
                "Reading tiles" to
                    "${sub.tilesDecoded} decoded · ${sub.tilesDetected} detected"
            } else {
                "Scanning" to "Move slowly across the mosaic."
            }
        }
        is SessionState.Stopping -> "Stopping" to state.reason
        SessionState.Finished -> "Finished" to "The file has been reconstructed."
    }
    Text(text = title, style = MaterialTheme.typography.titleLarge, color = PrismColors.TextPrimary)
    Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = PrismColors.TextSecondary)
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
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = PrismColors.TextPrimary,
                )
            }
        }
    }
}
