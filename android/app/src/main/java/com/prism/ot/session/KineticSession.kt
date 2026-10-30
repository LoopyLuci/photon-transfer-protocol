package com.prism.ot.session

import com.prism.ot.capture.CameraController
import com.prism.ot.capture.CameraSessionInfo
import com.prism.ot.capture.PrismFrameAnalyzer
import com.prism.ot.capture.RollingShutterEstimator
import com.prism.ot.capture.ScanServiceController
import com.prism.ot.codec.PrismCodec
import com.prism.ot.codec.SessionConfig
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.DecodedTile
import com.prism.ot.data.model.TransferMode
import com.prism.ot.di.CodecDispatcher
import com.prism.ot.di.IoDispatcher
import com.prism.ot.io.ReceivedFileStore
import com.prism.ot.io.Sha256Verifier
import com.prism.ot.ml.MlBridge
import com.prism.ot.ml.model.FaceTrackState
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.motor.BleMotorService
import com.prism.ot.motor.FaceRateMatcher
import com.prism.ot.motor.MotorConnectionState
import com.prism.ot.motor.MotorController
import com.prism.ot.motor.MotorPolicyLoop
import com.prism.ot.transfer.TransferSession
import com.prism.ot.transfer.TransferSessionImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

/**
 * Kinetic rotating‑mass session.
 *
 * Differences from [StaticSession]:
 *  1. The motor is connected before scanning begins. If the user has not
 *     connected a motor, the session still runs but uses the mass's
 *     natural or hand‑driven rotation; the policy loop is then started in
 *     "advisory" mode and only logs suggestions.
 *  2. The M8 face tracker is consulted on every frame. Faces whose blur
 *     estimate exceeds the decoder's tolerance are dropped before their
 *     tiles are ingested. This is the rolling‑shutter‑aware filtering that
 *     makes the kinetic link usable on older hardware with slow readout.
 *  3. The policy loop receives the measured camera FPS and the actual
 *     face‑decode rate, and writes a clamped rotation frequency to the
 *     motor.
 */
class KineticSession(
    cameraController: CameraController,
    frameAnalyzer: PrismFrameAnalyzer,
    scanService: ScanServiceController,
    mlBridge: MlBridge,
    sha256Verifier: Sha256Verifier,
    logger: PrismLogger,
    @CodecDispatcher codecDispatcher: CoroutineDispatcher,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    private val prismCodec: PrismCodec,
    private val fileStore: ReceivedFileStore,
    private val motorController: MotorController,
    private val motorPolicyLoop: MotorPolicyLoop,
    private val faceRateMatcher: FaceRateMatcher,
    private val rollingShutter: RollingShutterEstimator,
    private val sessionConfig: SessionConfig,
    private val faceCount: Int,
    private val preferredRotationHz: Float,
) : BaseScanSession(
    cameraController,
    frameAnalyzer,
    scanService,
    mlBridge,
    sha256Verifier,
    logger,
    codecDispatcher,
    ioDispatcher,
) {

    override val mode: TransferMode = TransferMode.KINETIC

    private var transferSession: TransferSession? = null
    private var lastTrackState: FaceTrackState? = null
    private var facesDetected = 0
    private var facesDecoded = 0
    private var motorConnected = false
    private var commandedHz = 0f
    private var cameraFps = 0f

    override fun requiredModels(): List<MlModelId> = listOf(
        MlModelId.M01_DECODABILITY,
        MlModelId.M02_LOCALISER,
        MlModelId.M04_CALIBRATOR,
        MlModelId.M05_CLASSIFIER,
        MlModelId.M06_NEURAL_LDPC,
        MlModelId.M08_FACE_TRACKER,
        MlModelId.M09_DEBLURRER,
        MlModelId.M10_MOTOR_POLICY,
    )

    override fun initialSubState(): RunningSubState = RunningSubState.KineticScanning(
        facesDetected = 0,
        facesDecoded = 0,
        motorConnected = false,
        commandedRotationHz = 0f,
    )

    override suspend fun onCameraBound(info: CameraSessionInfo): PrismResult<Unit> {
        cameraFps = info.nominalFps.toFloat()
        motorConnected = motorController.connectionState.value is MotorConnectionState.Ready
        if (motorConnected) {
            BleMotorService.start(context = requireContext())
            startPolicyLoop()
            val clamped = faceRateMatcher.clamp(preferredRotationHz, faceCount, cameraFps)
            commandedHz = clamped.hz
            motorController.setTargetRotationHz(commandedHz)
        }
        updateSubState(
            RunningSubState.KineticScanning(
                facesDetected = 0,
                facesDecoded = 0,
                motorConnected = motorConnected,
                commandedRotationHz = commandedHz,
            ),
        )
        return PrismResult.Success(Unit)
    }

    override suspend fun onTileDecoded(tile: DecodedTile) {
        // Rolling-shutter gate: if the mass is moving faster than the
        // sensor's readout can tolerate, the tile is likely sheared beyond
        // recovery. Count it as detected but not decoded.
        val track = lastTrackState
        if (track != null && !rollingShutter.isFrameUsable(
                skewNanos = currentSkewNanos(),
                angularVelocityRadPerSec = track.angularVelocityRadPerSec,
                tileAngularSizeRad = TILE_ANGULAR_SIZE_RAD,
            )
        ) {
            facesDetected++
            updateSubState(currentSubState())
            return
        }

        val existing = transferSession
        if (existing == null) {
            createTransfer().getOrNull() ?: return
        }
        val transfer = transferSession ?: return
        val completed = transfer.ingestTile(tile).getOrNull() ?: return
        facesDetected++
        facesDecoded++

        updateSubState(
            RunningSubState.KineticScanning(
                facesDetected = facesDetected,
                facesDecoded = facesDecoded,
                motorConnected = motorConnected,
                commandedRotationHz = commandedHz,
            ),
        )

        if (completed) {
            val finalized = transfer.finalize().getOrNull() ?: return
            publishTransferResult(finalized)
            runCatching { stop() }
        }
    }

    /**
     * Feeds the latest M8 track state into the session so the rolling
     * shutter gate has fresh angular velocity. Called by the frame
     * analyzer's channel‑state callback (wired in the ViewModel).
     */
    fun onTrackStateUpdated(track: FaceTrackState) {
        lastTrackState = track
        cameraFps = cameraController.actualFps.value.coerceAtLeast(1f)
    }

    private fun startPolicyLoop() {
        motorPolicyLoop.start(
            scope = scope,
            faceCount = faceCount,
            cameraFpsProvider = { cameraController.actualFps.value.coerceAtLeast(1f) },
            decodeRateProvider = {
                if (facesDetected == 0) 0f
                else facesDecoded.toFloat() / facesDetected.toFloat()
            },
            blurProvider = {
                lastTrackState?.blurSigmaByFace?.maxOrNull() ?: 0f
            },
        )
        scope.launch {
            motorPolicyLoop.policyState.collect { state ->
                commandedHz = state.commandedHz
                motorConnected = motorController.connectionState.value is MotorConnectionState.Ready
                updateSubState(currentSubState())
            }
        }
    }

    private suspend fun createTransfer(): PrismResult<TransferSession> {
        val created = TransferSessionImpl.create(
            codec = prismCodec,
            config = sessionConfig,
            metadata = PLACEHOLDER_METADATA,
            mode = TransferMode.KINETIC,
            fileStore = fileStore,
            mlBridge = mlBridge,
            codecDispatcher = codecDispatcher,
            logger = logger,
        )
        created.onSuccess { session ->
            session.start()
            scope.launch {
                session.progress.collect { progress -> emitProgress(progress) }
            }
            scope.launch {
                session.result.collect { result -> publishTransferResult(result) }
            }
        }
        created.onSuccess { transferSession = it }
        return created
    }

    override suspend fun onStopSession() {
        motorPolicyLoop.stop()
        if (motorConnected) {
            BleMotorService.stop(requireContext())
            runCatching { motorController.disconnect() }
        }
        transferSession?.cancel("session stop")
        transferSession = null
    }

    override suspend fun onCancelSession(reason: String) {
        transferSession?.cancel(reason)
    }

    private fun currentSubState(): RunningSubState = RunningSubState.KineticScanning(
        facesDetected = facesDetected,
        facesDecoded = facesDecoded,
        motorConnected = motorConnected,
        commandedRotationHz = commandedHz,
    )

    private fun currentSkewNanos(): Long =
        cameraController.sessionInfo.value?.rollingShutterSkewNanos ?: 0L

    private fun requireContext(): android.content.Context =
        previewView?.context ?: error("PreviewView not bound")

    companion object {
        /**
         * Angular size of one face as seen from a typical 40 cm viewing
         * distance with a 10 cm face. Used by the rolling‑shutter gate;
         * refined by M8 when tracking is active.
         */
        private const val TILE_ANGULAR_SIZE_RAD = 0.25f

        private val PLACEHOLDER_METADATA = com.prism.ot.data.model.FileMetadata(
            fileName = "pending",
            mimeType = "application/octet-stream",
            sizeBytes = 0L,
            sha256Hex = "0".repeat(64),
            segmentSizeBytes = com.prism.ot.core.PrismConstants.FOUNTAIN_SEGMENT_BYTES,
            segmentCount = 0,
            formatVersion = com.prism.ot.core.PrismConstants.FORMAT_VERSION,
            createdAtEpochMs = 0L,
        )
    }
}
