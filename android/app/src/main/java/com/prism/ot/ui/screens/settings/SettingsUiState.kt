package com.prism.ot.ui.screens.settings

import com.prism.ot.data.model.TransferMode
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.ml.model.ModelStatus

data class SettingsUiState(
    val preferredMode: TransferMode = TransferMode.KINETIC,
    val autoDetectMode: Boolean = true,
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val debugOverlay: Boolean = false,
    val faceBytes: Int = 7_500,
    val facesPerMass: Int = 16,
    val staticDpi: Int = 600,
    val rotationHz: Float = 0.25f,
    val modelStatuses: Map<MlModelId, ModelStatus> = emptyMap(),
    val totalBytesTransferred: Long = 0L,
    val totalSessionsCompleted: Int = 0,
    val showClearDataConfirmation: Boolean = false,
)
