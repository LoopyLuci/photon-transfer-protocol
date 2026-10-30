package com.prism.ot.ml

import android.graphics.Bitmap
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.ChannelState
import com.prism.ot.ml.model.AperiodicTileDetection
import com.prism.ot.ml.model.DeblurResult
import com.prism.ot.ml.model.DecodabilityScore
import com.prism.ot.ml.model.FaceTrackState
import com.prism.ot.ml.model.FountainPolicy
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.ml.model.ModelStatus
import com.prism.ot.ml.model.PrintDefectReport
import com.prism.ot.ml.model.SoftSymbolProbabilities
import com.prism.ot.ml.model.SuperResolutionResult
import com.prism.ot.ml.wrapper.CalibratorWrapper
import com.prism.ot.ml.wrapper.ChannelTransformerWrapper
import com.prism.ot.ml.wrapper.ClassifierWrapper
import com.prism.ot.ml.wrapper.DeblurrerWrapper
import com.prism.ot.ml.wrapper.DecodabilityWrapper
import com.prism.ot.ml.wrapper.DefectInspectorWrapper
import com.prism.ot.ml.wrapper.FaceTrackerWrapper
import com.prism.ot.ml.wrapper.FountainPolicyWrapper
import com.prism.ot.ml.wrapper.LocaliserWrapper
import com.prism.ot.ml.wrapper.MotorPolicyWrapper
import com.prism.ot.ml.wrapper.NeuralLdpcWrapper
import com.prism.ot.ml.wrapper.SyntheticChannelWrapper
import com.prism.ot.ml.wrapper.SuperResolutionWrapper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrator for the cognitive plane. Delegates every model call to its
 * wrapper, normalises errors, and keeps a single native handle for the
 * Rust ZOI path.
 *
 * Threading: each wrapper owns its own executor / interpreter; this class
 * only marshals calls and is safe to invoke from any dispatcher.
 */
@Singleton
class MlBridgeImpl @Inject constructor(
    private val logger: PrismLogger,
    private val native: NativeMlCore,
    private val decodability: DecodabilityWrapper,
    private val localiser: LocaliserWrapper,
    private val superResolution: SuperResolutionWrapper,
    private val calibrator: CalibratorWrapper,
    private val classifier: ClassifierWrapper,
    private val neuralLdpc: NeuralLdpcWrapper,
    private val fountainPolicy: FountainPolicyWrapper,
    private val faceTracker: FaceTrackerWrapper,
    private val deblurrer: DeblurrerWrapper,
    private val motorPolicy: MotorPolicyWrapper,
    private val channelTransformer: ChannelTransformerWrapper,
    private val defectInspector: DefectInspectorWrapper,
    private val syntheticChannel: SyntheticChannelWrapper,
) : MlBridge {

    @Volatile private var initialised = false

    override suspend fun initialize(): PrismResult<Unit> = guarded("initialize") {
        if (initialised) return@guarded
        decodability.initialize()
        localiser.initialize()
        superResolution.initialize()
        calibrator.initialize()
        classifier.initialize()
        neuralLdpc.initialize()
        fountainPolicy.initialize()
        faceTracker.initialize()
        deblurrer.initialize()
        motorPolicy.initialize()
        channelTransformer.initialize()
        defectInspector.initialize()
        syntheticChannel.initialize()
        initialised = true
    }

    override fun nativeVersion(): String = native.version()

    override fun listMissingModelAssets(): List<String> = classifier.listMissingAssets() +
        decodability.listMissingAssets() +
        localiser.listMissingAssets() +
        superResolution.listMissingAssets() +
        calibrator.listMissingAssets() +
        neuralLdpc.listMissingAssets() +
        fountainPolicy.listMissingAssets() +
        faceTracker.listMissingAssets() +
        deblurrer.listMissingAssets() +
        motorPolicy.listMissingAssets() +
        channelTransformer.listMissingAssets() +
        defectInspector.listMissingAssets() +
        syntheticChannel.listMissingAssets()

    override fun modelStatus(id: MlModelId): ModelStatus =
        when (id) {
            MlModelId.M01_DECODABILITY -> decodability.status()
            MlModelId.M02_LOCALISER -> localiser.status()
            MlModelId.M03_SUPER_RESOLUTION -> superResolution.status()
            MlModelId.M04_CALIBRATOR -> calibrator.status()
            MlModelId.M05_CLASSIFIER -> classifier.status()
            MlModelId.M06_NEURAL_LDPC -> neuralLdpc.status()
            MlModelId.M07_FOUNTAIN_POLICY -> fountainPolicy.status()
            MlModelId.M08_FACE_TRACKER -> faceTracker.status()
            MlModelId.M09_DEBLURRER -> deblurrer.status()
            MlModelId.M10_MOTOR_POLICY -> motorPolicy.status()
            MlModelId.M11_CHANNEL_TRANSFORMER -> channelTransformer.status()
            MlModelId.M12_DEFECT_INSPECTOR -> defectInspector.status()
            MlModelId.M13_CHANNEL_GENERATOR -> syntheticChannel.status()
        }

    // ---- M1 ----
    override suspend fun scoreDecodability(frame: Bitmap): PrismResult<DecodabilityScore> =
        guarded("M1") { decodability.score(frame) }

    // ---- M2 ----
    override suspend fun detectAperiodicTiles(
        frame: Bitmap,
    ): PrismResult<List<AperiodicTileDetection>> =
        guarded("M2") { localiser.detect(frame, native.spectreTemplateBank()) }

    // ---- M3 ----
    override suspend fun restoreTile(
        tile: Bitmap,
        estimatedBlurSigmaPx: Float,
    ): PrismResult<SuperResolutionResult> =
        guarded("M3") { superResolution.restore(tile, estimatedBlurSigmaPx) }

    // ---- M4 ----
    override suspend fun calibrateChromatic(
        pilotPatches: List<ChromaticPilot>,
    ): PrismResult<ChromaticCalibration> =
        guarded("M4") { calibrator.calibrate(pilotPatches) }

    // ---- M5 ----
    override suspend fun classifySymbols(
        rectifiedTile: Bitmap,
        calibration: ChromaticCalibration,
    ): PrismResult<SoftSymbolProbabilities> =
        guarded("M5") { classifier.classify(rectifiedTile, calibration) }

    // ---- M6 ----
    override suspend fun neuralLdpcDecode(
        softSymbols: SoftSymbolProbabilities,
        parityCheckMatrixId: Int,
    ): PrismResult<ByteArray> =
        guarded("M6") { neuralLdpc.decode(softSymbols, parityCheckMatrixId) }

    // ---- M7 ----
    override suspend fun selectFountainPolicy(
        channelState: ChannelState,
        fileSizeBytes: Long,
    ): PrismResult<FountainPolicy> =
        guarded("M7") { fountainPolicy.select(channelState, fileSizeBytes) }

    // ---- M8 ----
    override suspend fun trackRotatingMass(
        recentFrames: List<Bitmap>,
        previousState: FaceTrackState?,
    ): PrismResult<FaceTrackState> =
        guarded("M8") { faceTracker.track(recentFrames, previousState) }

    // ---- M9 ----
    override suspend fun deblurFace(
        faceFrame: Bitmap,
        estimatedAngularVelocityRadPerSec: Float,
        shutterNanos: Long,
    ): PrismResult<DeblurResult> =
        guarded("M9") { deblurrer.deblur(faceFrame, estimatedAngularVelocityRadPerSec, shutterNanos) }

    // ---- M10 ----
    override suspend fun suggestRotationHz(
        cameraFps: Float,
        currentDecodeRate: Float,
        currentBlurSigmaPx: Float,
    ): PrismResult<Float> =
        guarded("M10") { motorPolicy.suggest(cameraFps, currentDecodeRate, currentBlurSigmaPx) }

    // ---- M11 ----
    override suspend fun updateChannelState(
        features: ChannelFeatureVector,
        history: List<ChannelState>,
    ): PrismResult<ChannelState> =
        guarded("M11") { channelTransformer.update(features, history) }

    // ---- M12 ----
    override suspend fun inspectPrintedMosaic(page: Bitmap): PrismResult<PrintDefectReport> =
        guarded("M12") { defectInspector.inspect(page) }

    // ---- M13 ----
    override suspend fun synthesizeChannelSample(
        cleanTile: Bitmap,
        targetBlurSigmaPx: Float,
        targetColorShiftDelta: Float,
        targetNoiseDb: Float,
    ): PrismResult<Bitmap> =
        guarded("M13") {
            syntheticChannel.synthesize(cleanTile, targetBlurSigmaPx, targetColorShiftDelta, targetNoiseDb)
        }

    override suspend fun releaseAll() {
        runCatching { decodability.close() }
        runCatching { localiser.close() }
        runCatching { superResolution.close() }
        runCatching { calibrator.close() }
        runCatching { classifier.close() }
        runCatching { neuralLdpc.close() }
        runCatching { fountainPolicy.close() }
        runCatching { faceTracker.close() }
        runCatching { deblurrer.close() }
        runCatching { motorPolicy.close() }
        runCatching { channelTransformer.close() }
        runCatching { defectInspector.close() }
        runCatching { syntheticChannel.close() }
        initialised = false
    }

    /**
     * Uniform error envelope. Wrappers may throw; we convert to PrismError
     * so callers never need try/catch around ML calls.
     */
    private inline fun <T> guarded(model: String, block: () -> T): PrismResult<T> =
        try {
            PrismResult.Success(block())
        } catch (t: Throwable) {
            logger.e(TAG, "$model inference failed: ${t.message}", t)
            PrismResult.Failure(
                PrismError.ModelInferenceFailed(model, t.message ?: t.javaClass.simpleName),
                t,
            )
        }

    companion object {
        private const val TAG = "MlBridge"
    }
}
