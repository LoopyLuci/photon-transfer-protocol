package com.prism.ot.ui.screens.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.prism.ot.data.model.TransferMode
import com.prism.ot.ui.components.PrismTopBar
import com.prism.ot.ui.components.formatBytes
import com.prism.ot.ui.components.formatEta
import com.prism.ot.ui.theme.PrismColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Result detail screen.
 *
 * In the current batch the result is reconstructed from the navigation
 * argument only; Batch 11's result cache populates the full metadata.
 * The screen is intentionally read-only: a completed transfer's job is
 * done, and the only actions are "open" (handled by the delivering
 * Intent) and "back".
 */
@Composable
fun ResultDetailScreen(
    sessionResultId: String,
    onBack: () -> Unit,
    viewModel: ResultDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = PrismColors.Background,
        topBar = { PrismTopBar(title = "Transfer result", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PrismColors.Surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = uiState.fileName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = PrismColors.TextPrimary,
                        )
                        Text(
                            text = uiState.mimeType,
                            style = MaterialTheme.typography.labelSmall,
                            color = PrismColors.TextSecondary,
                        )
                        HorizontalDivider(color = PrismColors.Outline)
                        DetailRow("Size", formatBytes(uiState.sizeBytes))
                        DetailRow("Mode", uiState.mode.displayName)
                        DetailRow("Duration", formatEta(uiState.elapsedMillis))
                        DetailRow("Frames consumed", uiState.framesConsumed.toString())
                        DetailRow("SHA-256", uiState.sha256Short)
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (uiState.verified) {
                        PrismColors.Success.copy(alpha = 0.12f)
                    } else {
                        PrismColors.Error.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (uiState.verified) {
                            "✓ SHA-256 verified — the file is intact."
                        } else {
                            "Integrity check did not match. The file was discarded."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.verified) PrismColors.Success else PrismColors.Error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item {
                Text(
                    text = "The file is available through the system share sheet. " +
                        "For APK payloads, the installer was opened automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrismColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = PrismColors.TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = PrismColors.TextPrimary)
    }
}

@HiltViewModel
class ResultDetailViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(
        ResultDetailUiState(
            fileName = "Transfer",
            mimeType = "application/octet-stream",
            sizeBytes = 0L,
            mode = TransferMode.STATIC,
            elapsedMillis = 0L,
            framesConsumed = 0L,
            sha256Short = "—",
            verified = false,
        ),
    )
    val uiState: StateFlow<ResultDetailUiState> = _uiState.asStateFlow()
}

data class ResultDetailUiState(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val mode: TransferMode,
    val elapsedMillis: Long,
    val framesConsumed: Long,
    val sha256Short: String,
    val verified: Boolean,
)
