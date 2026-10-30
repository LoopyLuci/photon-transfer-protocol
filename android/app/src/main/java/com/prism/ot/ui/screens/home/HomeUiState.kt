package com.prism.ot.ui.screens.home

import com.prism.ot.data.model.TransferMode

/**
 * Home screen state.
 *
 * [startupState] is separate from [contentState] because the splash screen
 * is held on startup readiness while the rest of the screen can render
 * independently once the activity is visible.
 */
data class HomeUiState(
    val startupState: StartupState = StartupState.Initialising,
    val contentState: HomeContentState = HomeContentState.Loading,
)

sealed class StartupState {
    data object Initialising : StartupState()
    data object Ready : StartupState()
    data class Failed(val message: String) : StartupState()
}

data class HomeContentState(
    val totalSessionsCompleted: Int = 0,
    val totalBytesTransferred: Long = 0L,
    val preferredMode: TransferMode = TransferMode.KINETIC,
    val recentResults: List<RecentTransfer> = emptyList(),
    val mlModelsReady: Boolean = false,
    val missingModels: List<String> = emptyList(),
)

data class RecentTransfer(
    val sessionId: String,
    val fileName: String,
    val sizeBytes: Long,
    val completedAtEpochMs: Long,
    val mode: TransferMode,
)
