package com.prism.ot.session

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.prism.ot.capture.CameraController
import com.prism.ot.capture.PrismFrameAnalyzer
import com.prism.ot.capture.ScanServiceController
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.model.TransferProgress
import com.prism.ot.data.model.TransferResult
import com.prism.ot.di.CodecDispatcher
import com.prism.ot.di.IoDispatcher
import com.prism.ot.io.Sha256Verifier
import com.prism.ot.ml.MlBridge
import com.prism.ot.ml.model.ModelStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Shared implementation for [StaticSession] and [KineticSession].
 *
 * Responsibilities:
 *  - Owns the session coroutine scope. Every child coroutine is cancelled
 *    by [stop] and [cancel].
 *  - Binds the camera and wires the [PrismFrameAnalyzer] tile sink into
 *    the subclass's tile handler.
 *  - Starts and stops the [ScanServiceController] foreground service.
 *  - Normalises errors into [SessionResult].
 *
 * Subclasses override [onTileDecoded] to route tiles into their
 * `TransferSession`, and [onCameraBound] to perform mode‑specific setup
 * (e.g. connecting the motor in kinetic mode).
 */
abstract class BaseScanSession(
    protected val cameraController: CameraController,
    protected val frameAnalyzer: PrismFrameAnalyzer,
    protected val scanService: ScanServiceController,
    protected val mlBridge: MlBridge,
    protected val sha256Verifier: Sha256Verifier,
    protected val logger: PrismLogger,
    @CodecDispatcher protected val codecDispatcher: CoroutineDispatcher,
    @IoDispatcher protected val ioDispatcher: CoroutineDispatcher,
) : ScanSession {

    override val sessionId: String = UUID.randomUUID().toString()
    abstract override val mode: TransferMode

    protected val scope = CoroutineScope(SupervisorJob() + codecDispatcher)

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _progress = MutableStateFlow<TransferProgress?>(null)
    override val progress: StateFlow<TransferProgress?> = _progress.asStateFlow()

    private val _result = MutableStateFlow<SessionResult?>(null)
    override val result: StateFlow<SessionResult?> = _result.asStateFlow()

    private val startMutex = Mutex()
    private val stopMutex = Mutex()
    @Volatile private var started = false
    @Volatile private var stopped = false

    protected var previewView: PreviewView? = null
        private set

    // ---- Lifecycle ----

    override suspend fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ): PrismResult<Unit> = startMutex.withLock {
        if (started) return@withLock PrismResult.Success(Unit)
        started = true
        stopped = false
        _state.value = SessionState.Preparing

        // 1. Verify the ML models that this session depends on.
        val requiredModels = requiredModels()
        for (model in requiredModels) {
            if (mlBridge.modelStatus(model) is ModelStatus.Failed) {
                val err = PrismError.ModelLoadFailed(
                    model.displayName,
                    "model failed to load; see logs",
                )
                finishWithError(err)
                return@withLock PrismResult.Failure(err)
            }
        }

        // 2. Start the camera foreground service before binding the camera.
        //    Camera while-in-use rules require the app to be in the
        //    foreground at this point; the ViewModel calls start from
        //    onResume, which satisfies that requirement.
        runCatching { scanService.start() }
            .onFailure { logger.w(TAG, "Scan service start failed: ${it.message}") }

        // 3. Bind the camera with the analyzer.
        val bind = cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            analyzer = frameAnalyzer,
            mode = mode,
        ).fold(
            onSuccess = { it },
            onFailure = { err, cause ->
                runCatching { scanService.stop() }
                finishWithError(err)
                return@withLock PrismResult.Failure(err, cause)
            },
        )
        this.previewView = previewView

        // 4. Wire the tile sink into the subclass handler.
        frameAnalyzer.setTileSink { tile ->
            runCatching { onTileDecoded(tile) }
                .onFailure { logger.w(TAG, "onTileDecoded threw: ${it.message}") }
        }

        // 5. Mode-specific setup (e.g. motor connection in kinetic mode).
        onCameraBound(bind).fold(
            onSuccess = { it },
            onFailure = { err, cause ->
                runCatching { cameraController.unbind() }
                runCatching { scanService.stop() }
                finishWithError(err)
                return@withLock PrismResult.Failure(err, cause)
            },
        )

        _state.value = SessionState.Running(mode, initialSubState())
        logger.i(TAG, "Session $sessionId started in $mode mode")
        PrismResult.Success(Unit)
    }

    override suspend fun stop() = stopMutex.withLock {
        if (stopped) return@withLock
        stopped = true
        _state.value = SessionState.Stopping("stop")
        frameAnalyzer.setTileSink(null)
        runCatching { cameraController.unbind() }
        runCatching { scanService.stop() }
        runCatching { onStopSession() }
        scope.cancel()
        _state.value = SessionState.Finished
    }

    override suspend fun cancel(reason: String) {
        if (stopped) return
        _state.value = SessionState.Stopping(reason)
        runCatching { onCancelSession(reason) }
        stop()
        _result.value = SessionResult.Cancelled(
            bytesReceived = _progress.value?.bytesReceived ?: 0L,
            bytesTotal = _progress.value?.bytesTotal ?: 0L,
        )
    }

    // ---- Helpers for subclasses ----

    /** Models that must be loaded before this session can start. */
    protected abstract fun requiredModels(): List<com.prism.ot.ml.model.MlModelId>

    /** Initial running sub-state once the camera is bound. */
    protected abstract fun initialSubState(): RunningSubState

    /** Called when a tile is decoded by the analyzer. */
    protected abstract suspend fun onTileDecoded(tile: com.prism.ot.data.model.DecodedTile)

    /** Called once the camera is bound. Mode-specific setup. */
    protected abstract suspend fun onCameraBound(
        info: com.prism.ot.capture.CameraSessionInfo,
    ): PrismResult<Unit>

    /** Called from [stop]. Release transfer session, motor, etc. */
    protected abstract suspend fun onStopSession()

    /** Called from [cancel] before [stop]. */
    protected open suspend fun onCancelSession(reason: String) {}

    /** Emit a terminal transfer result. */
    protected suspend fun publishTransferResult(transfer: TransferResult) {
        when (transfer) {
            is TransferResult.Success -> _result.value = SessionResult.Completed(transfer)
            is TransferResult.Failed -> _result.value = SessionResult.TransferFailed(transfer)
            is TransferResult.Cancelled -> _result.value = SessionResult.Cancelled(
                transfer.bytesReceived,
                transfer.bytesTotal,
            )
        }
    }

    /** Emit an error and stop. */
    protected suspend fun finishWithError(error: PrismError) {
        logger.e(TAG, "Session error: ${error.code} — ${error.userMessage}")
        _result.value = SessionResult.Error(error)
        runCatching { stop() }
    }

    /** Update the running sub-state without changing the session state. */
    protected fun updateSubState(subState: RunningSubState) {
        val current = _state.value
        if (current is SessionState.Running) {
            _state.value = current.copy(subState = subState)
        }
    }

    /** Forward transfer progress from a `TransferSession`. */
    protected fun emitProgress(progress: TransferProgress) {
        _progress.value = progress
    }

    companion object {
        private const val TAG = "BaseScanSession"
    }
}
