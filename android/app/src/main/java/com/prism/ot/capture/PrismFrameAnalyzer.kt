package com.prism.ot.capture

import com.prism.ot.core.PrismConstants
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.ChannelFeatureVector
import com.prism.ot.data.model.DecodedTile
import com.prism.ot.data.model.FrameData
import com.prism.ot.ml.MlBridge
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production frame analyzer.
 *
 * Pipeline per frame:
 *   1. ImageProxy → Bitmap (hardware RGBA path on API 30+).
 *   2. M1 on a 224×224 downscale; reject the frame early if it is not
 *      worth decoding. This is the single largest power saving in the
 *      pipeline.
 *   3. M2 detects Spectre tiles. An empty result is a normal outcome, not
 *      an error.
 *   4. For each tile, bounded by [MAX_CONCURRENT_DECODERS]:
 *        rectify → extract pilots → M4 → M5 → M6 → DecodedTile.
 *   5. Emit each DecodedTile to the [TileSink].
 *
 * The analyzer deliberately does not own a codec session. It produces
 * fountain packets; the session owns the fountain decoder and the
 * SHA-256 verification.
 */
@Singleton
class PrismFrameAnalyzer @Inject constructor(
    private val mlBridge: MlBridge,
    private val frameConverter: FrameConverter,
    private val rollingShutter: RollingShutterEstimator,
    private val logger: PrismLogger,
) : FrameAnalyzer {

    private val decoderSemaphore = Semaphore(PrismConstants.MAX_CONCURRENT_DECODERS)

    @Volatile
    private var tileSink: TileSink? = null

    fun setTileSink(sink: TileSink?) {
        tileSink = sink
    }

    override suspend fun analyze(frame: FrameData): PrismResult<FrameAnalysisResult> =
        runCatching {
            val bitmap = frameConverter.toBitmap(frame.imageProxy, frame.rotationDegrees)
            try {
                val oracleInput = frameConverter.scaleForOracle(bitmap)
                val decodability = try {
                    mlBridge.scoreDecodability(oracleInput).getOrThrow()
                } finally {
                    if (oracleInput !== bitmap) oracleInput.recycle()
                }

                if (!decodability.isWorthDecoding) {
                    return@runCatching FrameAnalysisResult(
                        decodedTiles = emptyList(),
                        channelFeatures = buildChannelFeatures(frame, decodability, erasureRate = 1f),
                        decodability = decodability,
                        detections = 0,
                    )
                }

                val detections = mlBridge.detectAperiodicTiles(bitmap).getOrThrow()
                if (detections.isEmpty()) {
                    return@runCatching FrameAnalysisResult(
                        decodedTiles = emptyList(),
                        channelFeatures = buildChannelFeatures(frame, decodability, erasureRate = 1f),
                        decodability = decodability,
                        detections = 0,
                    )
                }

                val decoded = coroutineScope {
                    detections.map { detection ->
                        async {
                            decoderSemaphore.withPermit {
                                processTile(bitmap, detection, frame)
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                decoded.forEach { tile -> tileSink?.onTileDecoded(tile) }

                val erasure = if (detections.isEmpty()) 1f
                else 1f - (decoded.size.toFloat() / detections.size.toFloat())

                FrameAnalysisResult(
                    decodedTiles = decoded,
                    channelFeatures = buildChannelFeatures(frame, decodability, erasure),
                    decodability = decodability,
                    detections = detections.size,
                )
            } finally {
                bitmap.recycle()
            }
        }.fold(
            onSuccess = { PrismResult.Success(it) },
            onFailure = { t ->
                logger.e(TAG, "Frame analysis failed: ${t.message}", t)
                PrismResult.Failure(
                    PrismError.Unexpected(t.message ?: t.javaClass.simpleName),
                    t,
                )
            },
        )

    /**
     * Processes a single detection. Returns null when any stage fails;
     * failures are counted as erasures by the caller.
     */
    private suspend fun processTile(
        frame: android.graphics.Bitmap,
        detection: com.prism.ot.ml.model.AperiodicTileDetection,
        frameData: FrameData,
    ): DecodedTile? {
        val rectified = TileRectifier.rectify(frame, detection)
        return try {
            val pilots = PilotExtractor.extract(rectified)
            val calibration = mlBridge.calibrateChromatic(pilots).getOrNull() ?: return null
            val softSymbols = mlBridge.classifySymbols(rectified, calibration).getOrNull() ?: return null
            val packet = mlBridge.neuralLdpcDecode(softSymbols, PAYLOAD_PCM_ID).getOrNull() ?: return null

            DecodedTile(
                tileId = detection.centroidX.hashCode() * 31 +
                    detection.centroidY.hashCode() * 17 +
                    detection.orientationRadians.hashCode(),
                faceId = null,
                packetBytes = packet,
                symbolConfidence = softSymbols.topSymbolConfidence,
                wasErrorCorrected = softSymbols.entropy < ERROR_CORRECTION_ENTROPY_THRESHOLD,
                capturedAtEpochMs = frameData.timestampNanos / 1_000_000L,
            )
        } catch (t: Throwable) {
            logger.w(TAG, "Tile decode failed: ${t.message}", t)
            null
        } finally {
            rectified.recycle()
        }
    }

    private fun buildChannelFeatures(
        frame: FrameData,
        decodability: com.prism.ot.ml.model.DecodabilityScore,
        erasureRate: Float,
    ): ChannelFeatureVector = ChannelFeatureVector(
        blurSigmaPx = decodability.blurEstimateSigmaPx,
        colorResidualDeltaE00 = 0f, // populated by the calibrator in Batch 7's session glue
        snrProxyDb = decodability.score * 40f,
        recentErasureRate = erasureRate.coerceIn(0f, 1f),
        recentDecodeRate = (1f - erasureRate).coerceIn(0f, 1f),
        captureFps = frame.estimatedFps,
        perspectiveAngleDeg = 0f,
        frameTimestampNanos = frame.timestampNanos,
    )

    companion object {
        private const val TAG = "PrismFrameAnalyzer"

        /** Parity-check matrix index reserved for payload tiles. */
        const val PAYLOAD_PCM_ID = 1

        /**
         * Below this entropy the soft-symbol distribution is sharp enough
         * that the inner decoder almost certainly corrected something.
         * This is a heuristic for telemetry, not a correctness signal.
         */
        const val ERROR_CORRECTION_ENTROPY_THRESHOLD = 0.35f
    }
}
