package com.prism.ot.ui.screens.static

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.ot.core.PrismLogger
import com.prism.ot.data.model.TransferMode
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
 * Static scan ViewModel.
 *
 * Owns the [ScanSession] for the lifetime of the screen. The session is
 * created in [bindCamera] rather than in the constructor because it needs
 * a `LifecycleOwner` and a `PreviewView`, both of which are only available
 * once the Compose UI has been attached.
 *
 * Terminal results are surfaced through [uiState.terminalResult]; the
 * screen observes that field and calls [onResultConsumed] after navigating
 * so the event is not replayed on recomposition.
 */
@HiltViewModel
class StaticScanViewModel @Inject constructor(
    private val sessionFactory: SessionFactory,
    private val logger: PrismLogger,
) : ViewModel() {

    private val cameraReady = MutableStateFlow(false)
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    private val terminalResult = MutableStateFlow<SessionResult?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val showCancelConfirmation = MutableStateFlow(false)
    private val torchEnabled = MutableStateFlow(false)
    private val zoomRatio = MutableStateFlow(1f)

    private var session: ScanSession? = null

    val uiState: StateFlow<StaticScanUiState> = combine(
        cameraReady,
        sessionState,
        terminalResult,
        errorMessage,
        showCancelConfirmation,
    ) { ready, state, result, error, confirm ->
        StaticScanUiState(
            cameraReady = ready,
            sessionState = state,
            progress = null, // replaced by the session progress flow below
            terminalResult = result,
            errorMessage = error,
            showCancelConfirmation = confirm,
            torchEnabled = torchEnabled.value,
            zoomRatio = zoomRatio.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StaticScanUiState(),
    )

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        if (session != null) return
        viewModelScope.launch {
            val created = sessionFactory.create(TransferMode.STATIC)
            session = created

            viewModelScope.launch {
                created.state.collect { sessionState.value = it }
            }
            viewModelScope.launch {
                created.progress.collect { progress ->
                    // Progress is merged into the UI state through a
                    // separate flow; see the note in onProgressObserved.
                    latestProgress = progress
                }
            }
            viewModelScope.launch {
                created.result.collect { result ->
                    if (result != null) terminalResult.value = result
                }
            }

            created.start(lifecycleOwner, previewView).fold(
                onSuccess = { cameraReady.value = true },
                onFailure = { error, cause ->
                    logger.e(TAG, "Static session start failed", cause)
                    errorMessage.value = error.userMessage
                },
            )
        }
    }

    @Volatile
    private var latestProgress: com.prism.ot.data.model.TransferProgress? = null

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
        const val TAG = "StaticScanViewModel"
    }
}
