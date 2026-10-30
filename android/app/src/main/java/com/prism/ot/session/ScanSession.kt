package com.prism.ot.session

import androidx.lifecycle.LifecycleOwner
import androidx.camera.view.PreviewView
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.model.TransferProgress
import com.prism.ot.data.model.TransferResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for every PRISM scanning session.
 *
 * A session is a single-shot, UI-owned object. It is created when the user
 * enters a scanning screen and destroyed when they leave. It is **not** a
 * singleton and must never be injected into a `@Singleton` component.
 *
 * Lifecycle:
 *
 *   Created → start(...) → Running → { completed | failed | cancelled }
 *
 * `start` is idempotent: a second call while `Running` is a no-op. `cancel`
 * is safe to call from any state.
 */
interface ScanSession {

    val sessionId: String
    val mode: TransferMode

    /**
     * Current UI state. `StateFlow` (not `SharedFlow`) because the UI must
     * be able to re‑render after a configuration change without waiting
     * for the next emission.
     */
    val state: StateFlow<SessionState>

    /** Throttled transfer progress. `null` until the first tile is decoded. */
    val progress: StateFlow<TransferProgress?>

    /** Terminal result. Emits exactly once, then stays at the same value. */
    val result: StateFlow<SessionResult?>

    /**
     * Binds the session to the camera preview. Must be called from
     * `onResume` with a valid [LifecycleOwner] and [PreviewView].
     */
    suspend fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ): PrismResult<Unit>

    /**
     * Stops camera analysis and releases the transfer session. The camera
     * itself is unbound by [CameraController.unbind]; this method only
     * cancels coroutines and closes the codec session.
     */
    suspend fun stop()

    suspend fun cancel(reason: String = "user")
}

/**
 * High-level session state for the UI. The `Running` state carries the
 * per-mode sub‑state so the UI can render the correct overlay without
 * branching on the concrete session type.
 */
sealed class SessionState {

    data object Idle : SessionState()

    data object Preparing : SessionState()

    data class Running(
        val mode: TransferMode,
        val subState: RunningSubState,
    ) : SessionState()

    data class Stopping(val reason: String) : SessionState()

    data object Finished : SessionState()
}

/**
 * Mode‑specific running sub‑state.
 */
sealed class RunningSubState {
    /** Static mode: the scanner is looking for a mosaic or reading tiles. */
    data class StaticScanning(
        val tilesDetected: Int,
        val tilesDecoded: Int,
        val currentPageFraction: Float,
    ) : RunningSubState()

    /** Kinetic mode: the mass is connected and faces are being ingested. */
    data class KineticScanning(
        val facesDetected: Int,
        val facesDecoded: Int,
        val motorConnected: Boolean,
        val commandedRotationHz: Float,
    ) : RunningSubState()

    /** Auto‑mode detection is in progress. */
    data class DetectingMode(val samplesCollected: Int) : RunningSubState()
}

/**
 * Terminal session outcome. Distinct from [TransferResult] because a
 * session can fail before a transfer ever starts (e.g. camera bind error).
 */
sealed class SessionResult {

    data class Completed(val transfer: TransferResult.Success) : SessionResult()

    data class TransferFailed(val transfer: TransferResult.Failed) : SessionResult()

    data class Cancelled(val bytesReceived: Long, val bytesTotal: Long) : SessionResult()

    data class Error(val error: PrismError) : SessionResult()
}
