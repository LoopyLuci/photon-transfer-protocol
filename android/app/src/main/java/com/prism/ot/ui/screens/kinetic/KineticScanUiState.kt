package com.prism.ot.ui.screens.kinetic

import com.prism.ot.data.model.TransferProgress
import com.prism.ot.motor.MotorConnectionState
import com.prism.ot.motor.MotorTelemetry
import com.prism.ot.session.SessionResult
import com.prism.ot.session.SessionState

/**
 * Kinetic scan screen state. Adds the motor controls on top of the static
 * state: connection status, commanded rotation, telemetry, and the
 * policy-loop state that explains why the mass is at its current speed.
 */
data class KineticScanUiState(
    val cameraReady: Boolean = false,
    val sessionState: SessionState = SessionState.Idle,
    val progress: TransferProgress? = null,
    val terminalResult: SessionResult? = null,
    val errorMessage: String? = null,
    val showCancelConfirmation: Boolean = false,
    val torchEnabled: Boolean = false,
    val zoomRatio: Float = 1f,
    val motorState: MotorConnectionState = MotorConnectionState.Idle,
    val telemetry: MotorTelemetry? = null,
    val commandedRotationHz: Float = 0f,
    val facesDetected: Int = 0,
    val facesDecoded: Int = 0,
    val availableMotors: List<com.prism.ot.motor.DiscoveredMotor> = emptyList(),
    val isScanningForMotors: Boolean = false,
    val showMotorPicker: Boolean = false,
)
