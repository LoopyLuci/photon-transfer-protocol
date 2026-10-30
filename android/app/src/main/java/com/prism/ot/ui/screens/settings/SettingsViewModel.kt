package com.prism.ot.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.preferences.PrismPreferences
import com.prism.ot.ml.MlBridge
import com.prism.ot.ml.model.MlModelId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings ViewModel. Every preference is written through
 * [PrismPreferences] and read back through its flows, so the UI never
 * holds a divergent copy of the truth.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PrismPreferences,
    private val mlBridge: MlBridge,
) : ViewModel() {

    private val showClearDataConfirmation = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.preferredTransferMode,
        preferences.autoDetectMode,
        preferences.keepScreenOn,
    ) { mode, auto, keep ->
        Triple(mode, auto, keep)
    }.combine(preferences.hapticFeedbackEnabled) { acc, haptic ->
        acc to haptic
    }.combine(preferences.soundFeedbackEnabled) { acc, sound ->
        acc to sound
    }.combine(preferences.debugOverlayEnabled) { acc, debug ->
        acc to debug
    }.combine(preferences.preferredFaceBytes) { acc, faceBytes ->
        acc to faceBytes
    }.combine(preferences.preferredFacesPerMass) { acc, faces ->
        acc to faces
    }.combine(preferences.preferredStaticDpi) { acc, dpi ->
        acc to dpi
    }.combine(preferences.preferredRotationHz) { acc, hz ->
        acc to hz
    }.combine(preferences.totalBytesTransferred) { acc, bytes ->
        acc to bytes
    }.combine(preferences.totalSessionsCompleted) { acc, sessions ->
        acc to sessions
    }.combine(showClearDataConfirmation) { acc, confirm ->
        val (mode, auto) = acc.first
        val keep = acc.second.first
        val haptic = acc.second.second.first
        val sound = acc.second.second.second.first
        val debug = acc.second.second.second.second.first
        val faceBytes = acc.second.second.second.second.second.first
        val faces = acc.second.second.second.second.second.second.first
        val dpi = acc.second.second.second.second.second.second.second.first
        val hz = acc.second.second.second.second.second.second.second.second.first
        val bytes = acc.second.second.second.second.second.second.second.second.second.first
        val sessions = acc.second.second.second.second.second.second.second.second.second.second
        SettingsUiState(
            preferredMode = mode,
            autoDetectMode = auto,
            hapticEnabled = haptic,
            soundEnabled = sound,
            keepScreenOn = keep,
            debugOverlay = debug,
            faceBytes = faceBytes,
            facesPerMass = faces,
            staticDpi = dpi,
            rotationHz = hz,
            modelStatuses = MlModelId.entries.associateWith { mlBridge.modelStatus(it) },
            totalBytesTransferred = bytes,
            totalSessionsCompleted = sessions,
            showClearDataConfirmation = confirm,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setPreferredMode(mode: TransferMode) = viewModelScope.launch {
        preferences.setPreferredTransferMode(mode)
    }

    fun setAutoDetectMode(enabled: Boolean) = viewModelScope.launch {
        preferences.setAutoDetectMode(enabled)
    }

    fun setHapticEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setHapticFeedbackEnabled(enabled)
    }

    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setSoundFeedbackEnabled(enabled)
    }

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch {
        preferences.setKeepScreenOn(enabled)
    }

    fun setDebugOverlay(enabled: Boolean) = viewModelScope.launch {
        preferences.setDebugOverlayEnabled(enabled)
    }

    fun setFaceBytes(bytes: Int) = viewModelScope.launch {
        preferences.setPreferredFaceBytes(bytes)
    }

    fun setFacesPerMass(faces: Int) = viewModelScope.launch {
        preferences.setPreferredFacesPerMass(faces)
    }

    fun setStaticDpi(dpi: Int) = viewModelScope.launch {
        preferences.setPreferredStaticDpi(dpi)
    }

    fun setRotationHz(hz: Float) = viewModelScope.launch {
        preferences.setPreferredRotationHz(hz)
    }

    fun requestClearData() {
        showClearDataConfirmation.value = true
    }

    fun confirmClearData() {
        showClearDataConfirmation.value = false
        viewModelScope.launch { preferences.clearAll() }
    }

    fun dismissClearData() {
        showClearDataConfirmation.value = false
    }
}
