package com.prism.ot.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.ot.core.PrismLogger
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.preferences.PrismPreferences
import com.prism.ot.ml.MlBridge
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.ml.model.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home screen ViewModel.
 *
 * Owns two responsibilities:
 *  1. Report startup readiness to `MainActivity` so the splash screen can
 *     be released at the right moment.
 *  2. Assemble the home content state from preferences and the ML bridge.
 *
 * The UI state is a single `StateFlow` built with
 * `stateIn(WhileSubscribed(5_000))`, which keeps upstream flows alive for
 * five seconds across configuration changes and then stops them.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferences: PrismPreferences,
    private val mlBridge: MlBridge,
    private val logger: PrismLogger,
) : ViewModel() {

    private val startup = MutableStateFlow<StartupState>(StartupState.Initialising)

    val startupState: StateFlow<StartupState> = startup

    val uiState: StateFlow<HomeUiState> = combine(
        preferences.totalSessionsCompleted,
        preferences.totalBytesTransferred,
        preferences.preferredTransferMode,
    ) { sessions, bytes, mode ->
        HomeContentState(
            totalSessionsCompleted = sessions,
            totalBytesTransferred = bytes,
            preferredMode = mode,
            recentResults = emptyList(), // populated from a result cache in a later batch
            mlModelsReady = mlBridge.listMissingModelAssets().isEmpty(),
            missingModels = mlBridge.listMissingModelAssets(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeContentState(),
    )

    init {
        initialiseMl()
    }

    private fun initialiseMl() {
        viewModelScope.launch {
            val result = mlBridge.initialize()
            result.fold(
                onSuccess = {
                    val missing = mlBridge.listMissingModelAssets()
                    if (missing.isEmpty()) {
                        startup.value = StartupState.Ready
                    } else {
                        logger.w(TAG, "Missing ML assets: $missing")
                        startup.value = StartupState.Ready
                    }
                },
                onFailure = { error, cause ->
                    logger.e(TAG, "ML initialisation failed: ${error.code}", cause)
                    startup.value = StartupState.Failed(error.userMessage)
                },
            )
        }
    }

    fun setPreferredMode(mode: TransferMode) {
        viewModelScope.launch { preferences.setPreferredTransferMode(mode) }
    }

    fun modelStatus(id: MlModelId): ModelStatus = mlBridge.modelStatus(id)

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
