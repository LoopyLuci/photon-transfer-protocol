package com.prism.ot.session

import com.prism.ot.capture.CameraController
import com.prism.ot.capture.PrismFrameAnalyzer
import com.prism.ot.capture.ScanServiceController
import com.prism.ot.codec.PrismCodec
import com.prism.ot.codec.SessionConfig
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.preferences.PrismPreferences
import com.prism.ot.di.CodecDispatcher
import com.prism.ot.di.IoDispatcher
import com.prism.ot.io.ReceivedFileStore
import com.prism.ot.io.Sha256Verifier
import com.prism.ot.ml.MlBridge
import com.prism.ot.motor.FaceRateMatcher
import com.prism.ot.motor.MotorController
import com.prism.ot.motor.MotorPolicyLoop
import com.prism.ot.capture.RollingShutterEstimator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Constructs the correct [ScanSession] for a given mode and user
 * preferences.
 *
 * Why a factory instead of Hilt injection: sessions hold camera and
 * transfer state that must be scoped to a single screen. Injecting them
 * as singletons would leak the camera between screens and break the
 * while-in-use contract. The factory is a singleton; the sessions it
 * creates are not.
 */
@Singleton
class SessionFactory @Inject constructor(
    private val cameraController: CameraController,
    private val frameAnalyzer: PrismFrameAnalyzer,
    private val scanService: ScanServiceController,
    private val mlBridge: MlBridge,
    private val sha256Verifier: Sha256Verifier,
    private val prismCodec: PrismCodec,
    private val fileStore: ReceivedFileStore,
    private val motorController: MotorController,
    private val motorPolicyLoop: MotorPolicyLoop,
    private val faceRateMatcher: FaceRateMatcher,
    private val rollingShutter: RollingShutterEstimator,
    private val preferences: PrismPreferences,
    private val logger: com.prism.ot.core.PrismLogger,
    @CodecDispatcher private val codecDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun create(mode: TransferMode): ScanSession {
        val config = buildSessionConfig()
        return when (mode) {
            TransferMode.STATIC, TransferMode.FALLBACK -> StaticSession(
                cameraController = cameraController,
                frameAnalyzer = frameAnalyzer,
                scanService = scanService,
                mlBridge = mlBridge,
                sha256Verifier = sha256Verifier,
                logger = logger,
                codecDispatcher = codecDispatcher,
                ioDispatcher = ioDispatcher,
                prismCodec = prismCodec,
                fileStore = fileStore,
                sessionConfig = config,
            )
            TransferMode.KINETIC -> KineticSession(
                cameraController = cameraController,
                frameAnalyzer = frameAnalyzer,
                scanService = scanService,
                mlBridge = mlBridge,
                sha256Verifier = sha256Verifier,
                logger = logger,
                codecDispatcher = codecDispatcher,
                ioDispatcher = ioDispatcher,
                prismCodec = prismCodec,
                fileStore = fileStore,
                motorController = motorController,
                motorPolicyLoop = motorPolicyLoop,
                faceRateMatcher = faceRateMatcher,
                rollingShutter = rollingShutter,
                sessionConfig = config,
                faceCount = preferences.preferredFacesPerMass.first(),
                preferredRotationHz = preferences.preferredRotationHz.first(),
            )
        }
    }

    private suspend fun buildSessionConfig(): SessionConfig {
        val faceBytes = preferences.preferredFaceBytes.first()
        return SessionConfig(
            formatVersion = com.prism.ot.core.PrismConstants.FORMAT_VERSION,
            alphabetMode = com.prism.ot.codec.AlphabetMode.DENSE_64,
            innerCodeMode = com.prism.ot.codec.InnerCodeMode.NB_LDPC_GF64,
            outerCodeMode = com.prism.ot.codec.OuterCodeMode.RAPTORQ,
            segmentBytes = com.prism.ot.core.PrismConstants.FOUNTAIN_SEGMENT_BYTES,
            symbolBytes = faceBytes,
            targetOverhead = com.prism.ot.core.PrismConstants.FOUNTAIN_TARGET_OVERHEAD,
            distributionId = 0,
            useHierarchicalPartitioning = true,
            errorCorrectionBudget = 0.35f,
        )
    }
}
