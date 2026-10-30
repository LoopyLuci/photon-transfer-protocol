package com.prism.ot.ml

import android.graphics.Bitmap
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.ChannelState
import com.prism.ot.data.model.DecodedTile
import com.prism.ot.data.model.FountainPacket
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

/**
 * Public façade for the PRISM cognitive plane.
 *
 * All methods are suspend because every model may need to wait for a
 * LiteRT interpreter compilation, a GPU shader warm-up, or a native
 * ZOI buffer to be mapped. Implementations must be safe to call from
 * multiple coroutines.
 *
 * Return values are always [PrismResult] so callers can branch on
 * [com.prism.ot.core.PrismError] without catching exceptions.
 */
interface MlBridge {

    // ---- Lifecycle ----
    suspend fun initialize(): PrismResult<Unit>
    fun nativeVersion(): String
    fun listMissingModelAssets(): List<String>
    fun modelStatus(id: MlModelId): ModelStatus

    // ---- M1: Decodability Oracle ----
    suspend fun scoreDecodability(frame: Bitmap): PrismResult<DecodabilityScore>

    // ---- M2: Tile Localiser ----
    suspend fun detectAperiodicTiles(frame: Bitmap): PrismResult<List<AperiodicTileDetection>>

    // ---- M3: Super-Resolution Restorer ----
    suspend fun restoreTile(
        tile: Bitmap,
        estimatedBlurSigmaPx: Float,
    ): PrismResult<SuperResolutionResult>

    // ---- M4: Chromatic Calibrator ----
    suspend fun calibrateChromatic(
        pilotPatches: List<ChromaticPilot>,
    ): PrismResult<ChromaticCalibration>

    // ---- M5: Shape–Colour Classifier (ZOI) ----
    suspend fun classifySymbols(
        rectifiedTile: Bitmap,
        calibration: ChromaticCalibration,
    ): PrismResult<SoftSymbolProbabilities>

    // ---- M6: Neural Inner Decoder ----
    suspend fun neuralLdpcDecode(
        softSymbols: SoftSymbolProbabilities,
        parityCheckMatrixId: Int,
    ): PrismResult<ByteArray>

    // ---- M7: Fountain Degree Optimiser ----
    suspend fun selectFountainPolicy(
        channelState: ChannelState,
        fileSizeBytes: Long,
    ): PrismResult<FountainPolicy>

    // ---- M8: Face Tracker & Rotation Estimator ----
    suspend fun trackRotatingMass(
        recentFrames: List<Bitmap>,
        previousState: FaceTrackState?,
    ): PrismResult<FaceTrackState>

    // ---- M9: Motion Deblurrer ----
    suspend fun deblurFace(
        faceFrame: Bitmap,
        estimatedAngularVelocityRadPerSec: Float,
        shutterNanos: Long,
    ): PrismResult<DeblurResult>

    // ---- M10: Motor Synchroniser (policy inference only) ----
    suspend fun suggestRotationHz(
        cameraFps: Float,
        currentDecodeRate: Float,
        currentBlurSigmaPx: Float,
    ): PrismResult<Float>

    // ---- M11: Channel State Transformer ----
    suspend fun updateChannelState(
        features: ChannelFeatureVector,
        history: List<ChannelState>,
    ): PrismResult<ChannelState>

    // ---- M12: Print Defect Inspector ----
    suspend fun inspectPrintedMosaic(page: Bitmap): PrismResult<PrintDefectReport>

    // ---- M13: Synthetic Channel Generator (training/QA only) ----
    suspend fun synthesizeChannelSample(
        cleanTile: Bitmap,
        targetBlurSigmaPx: Float,
        targetColorShiftDelta: Float,
        targetNoiseDb: Float,
    ): PrismResult<Bitmap>

    // ---- Utility ----
    suspend fun releaseAll()
}

/**
 * A single calibration pilot patch with its intended CIELAB coordinate.
 * The receiver knows what the sender printed/displayed; the calibrator
 * learns the transform from received to intended.
 */
data class ChromaticPilot(
    val intendedL: Float,
    val intendedA: Float,
    val intendedB: Float,
    val receivedR: Int,
    val receivedG: Int,
    val receivedB: Int,
)

/**
 * Output of M4. [transformMatrix] is a 3×4 affine matrix in CIELAB
 * (row-major, 12 floats) plus a bias row. Apply as:
 *   labOut = transformMatrix * [L, a, b, 1]^T
 */
data class ChromaticCalibration(
    val transformMatrix: FloatArray,
    val residualDeltaE00: Float,
    val activeColorCount: Int,
) {
    init {
        require(transformMatrix.size == 12) { "Expected 3x4 affine matrix" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChromaticCalibration) return false
        return transformMatrix.contentEquals(other.transformMatrix) &&
            residualDeltaE00 == other.residualDeltaE00 &&
            activeColorCount == other.activeColorCount
    }

    override fun hashCode(): Int {
        var result = transformMatrix.contentHashCode()
        result = 31 * result + residualDeltaE00.hashCode()
        result = 31 * result + activeColorCount
        return result
    }
}

/**
 * Input features for M11. Produced by the capture pipeline from the
 * last N frames: blur estimate, colour residual, SNR proxy, erasure rate.
 */
data class ChannelFeatureVector(
    val blurSigmaPx: Float,
    val colorResidualDeltaE00: Float,
    val snrProxyDb: Float,
    val recentErasureRate: Float,
    val recentDecodeRate: Float,
    val captureFps: Float,
    val perspectiveAngleDeg: Float,
    val frameTimestampNanos: Long,
)
