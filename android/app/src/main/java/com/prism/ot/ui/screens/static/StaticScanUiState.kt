package com.prism.ot.ui.screens.static

import com.prism.ot.data.model.TransferProgress
import com.prism.ot.session.SessionResult
import com.prism.ot.session.SessionState

/**
 * Static scan screen state.
 *
 * [cameraReady] and [sessionState] are separate because the camera can be
 * bound before the session starts (during the permission handshake) and
 * the overlay must render the preview as soon as it is available.
 */
data class StaticScanUiState(
    val cameraReady: Boolean = false,
    val sessionState: SessionState = SessionState.Idle,
    val progress: TransferProgress? = null,
    val terminalResult: SessionResult? = null,
    val errorMessage: String? = null,
    val showCancelConfirmation: Boolean = false,
    val torchEnabled: Boolean = false,
    val zoomRatio: Float = 1f,
)
