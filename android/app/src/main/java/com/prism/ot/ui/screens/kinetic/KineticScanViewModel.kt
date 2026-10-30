package com.prism.ot.ui.screens.kinetic

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.ot.core.PrismLogger
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.preferences.PrismPreferences
import com.prism.ot.motor.MotorController
import com.prism.ot.session.RunningSubState
import com.prism.ot.session.ScanSession
import com.prism.ot.session.SessionFactory
import com.prism.ot.session.SessionResult
import com.prism.ot.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kinetic scan ViewModel.
 *
 * The kinetic screen is the only one that owns a motor connection. It
 * exposes three additional concerns beyond the static screen:
 *  - motor discovery and selection,
 *  - the live commanded rotation and telemetry readout,
 *  - the face-decode counters that show whether the face-rate match is
 *    actually working.
 */
@HiltViewModel
class KineticScanViewModel @Inject constructor(
    private val sessionFactory: SessionFactory,
    private val motorController: MotorController,
    private val preferences: PrismPreferences,
    private val logger: PrismLogger,
) : ViewModel() {

    private val cameraReady = MutableStateFlow(false)
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    private val terminalResult = MutableStateFlow<SessionResult?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val showCancelConfirmation = MutableStateFlow(false)
    private val torchEnabled = MutableStateFlow(false)
    private val zoomRatio = MutableStateFlow(1f)
    private val availableMotors = MutableStateFlow<List<com.prism.ot.motor.DiscoveredMotor>>(emptyList())
    private val isScanningForMotors = MutableStateFlow(false)
    private val showMotorPicker = MutableStateFlow(false)

    private var session: ScanSession? = null

    val uiState: StateFlow<KineticScanUiState> = combine(
        combine(cameraReady, sessionState, terminalResult) { ready, state, result ->
            Triple(ready, state, result)
        },
        combine(errorMessage, showCancelConfirmation, availableMotors) { error, confirm, motors ->
            Triple(error, confirm, motors)
        },
        combine(motorController.connectionState, motorController.telemetry, isScanningForMotors) { conn, telemetry, scanning ->
            Triple(conn, telemetry, scanning)
        },
    ) { (ready, state, result), (error, confirm, motors), (conn, telemetry, scanning) ->
        val subState = (state as? SessionState.Running)?.subState as? RunningSubState.KineticScanning
        KineticScanUiState(
            cameraReady = ready,
            sessionState = state,
            terminalResult = result,
            errorMessage = error,
            showCancelConfirmation = confirm,
            torchEnabled = torchEnabled.value,
            zoomRatio = zoomRatio.value,
            motorState = conn,
            telemetry = telemetry,
            commandedRotationHz = subState?.commandedRotationHz ?: 0f,
            facesDetected = subState?.facesDetected ?: 0,
            facesDecoded = subState?.facesDecoded ?: 0,
            availableMotors = motors,
            isScanningForMotors = scanning,
            showMotorPicker = showMotorPicker.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KineticScanUiState(),
    )

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        if (session != null) return
        viewModelScope.launch {
            val created = sessionFactory.create(TransferMode.KINETIC)
            session = created

            viewModelScope.launch { created.state.collect { sessionState.value = it } }
            viewModelScope.launch {
                created.result.collect { result ->
                    if (result != null) terminalResult.value = result
                }
            }

            created.start(lifecycleOwner, previewView).fold(
                onSuccess = { cameraReady.value = true },
                onFailure = { error, cause ->
                    logger.e(TAG, "Kinetic session start failed", cause)
                    errorMessage.value = error.userMessage
                },
            )
        }
    }

    fun startMotorDiscovery() {
        if (isScanningForMotors.value) return
        isScanningForMotors.value = true
        viewModelScope.launch {
            motorController.scanForMotors().fold(
                onSuccess = { motors ->
                    availableMotors.value = motors
                    showMotorPicker.value = motors.isNotEmpty()
                },
                onFailure = { error, _ ->
                    errorMessage.value = error.userMessage
                },
            )
            isScanningForMotors.value = false
        }
    }

    fun connectMotor(macAddress: String) {
        showMotorPicker.value = false
        viewModelScope.launch {
            motorController.connect(macAddress).fold(
                onSuccess = {
                    preferences.setLastUsedMotorMac(macAddress)
                },
                onFailure = { error, _ ->
                    errorMessage.value = error.userMessage
                },
            )
        }
    }

    fun disconnectMotor() {
        viewModelScope.launch { motorController.disconnect() }
    }

    fun dismissMotorPicker() {
        showMotorPicker.value = false
    }

    fun onResultConsumed() {
        terminalResult.value = null
    }

    fun requestCancel() {
        showCancelConfirmation.value = true
    }

    fun confirmCancel() {
        showCancelConfirmation.value = false
        viewModelScope.launch { session?.cancel("user") }
    }

    fun dismissCancel() {
        showCancelConfirmation.value = false
    }

    fun toggleTorch() {
        torchEnabled.value = !torchEnabled.value
    }

    fun setZoom(ratio: Float) {
        zoomRatio.value = ratio.coerceIn(1f, 5f)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { session?.stop() }
        session = null
    }

    private companion object {
        const val TAG = "KineticScanViewModel"
    }
}
