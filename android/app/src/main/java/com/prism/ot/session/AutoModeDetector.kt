package com.prism.ot.session

import android.graphics.Bitmap
import com.prism.ot.core.PrismLogger
import com.prism.ot.data.model.TransferMode
import com.prism.ot.ml.MlBridge
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether the scene contains a static mosaic or a rotating mass.
 *
 * The detector is deliberately cheap: it runs M1 + M2 on a handful of
 * frames and looks at the temporal behaviour of the detections.
 *
 *  - A static mosaic produces detections whose centroids and orientations
 *    are stable across frames.
 *  - A rotating mass produces detections whose centroids move coherently
 *    and whose orientations change monotonically.
 *
 * The classifier is a simple variance test, not a neural network. A
 * learned mode classifier would be more accurate but would require a
 * training set of labelled static and kinetic scenes, which is exactly the
 * kind of data that does not exist before the product ships. The variance
 * test is interpretable, has no model asset, and is correct for the two
 * scene classes PRISM actually supports.
 */
@Singleton
class AutoModeDetector @Inject constructor(
    private val mlBridge: MlBridge,
    private val logger: PrismLogger,
) {

    /**
     * Observes [sampleCount] frames and returns the detected mode, or
     * `null` if the evidence is inconclusive (in which case the caller
     * should ask the user).
     */
    suspend fun detect(
        frameProvider: suspend () -> Bitmap?,
        sampleCount: Int = 5,
        intervalMillis: Long = 300L,
    ): TransferMode? {
        val centroidsX = mutableListOf<Float>()
        val centroidsY = mutableListOf<Float>()
        val orientations = mutableListOf<Float>()

        repeat(sampleCount) {
            val frame = frameProvider() ?: return@repeat
            try {
                val detections = mlBridge.detectAperiodicTiles(frame).getOrNull().orEmpty()
                if (detections.isNotEmpty()) {
                    centroidsX += detections.map { it.centroidX }.average().toFloat()
                    centroidsY += detections.map { it.centroidY }.average().toFloat()
                    orientations += detections.map { it.orientationRadians }.average().toFloat()
                }
            } finally {
                frame.recycle()
            }
            delay(intervalMillis)
        }

        if (centroidsX.size < MIN_SAMPLES) return null

        val xVariance = variance(centroidsX)
        val yVariance = variance(centroidsY)
        val orientationVariance = variance(orientations)

        logger.d(
            TAG,
            "Mode detection: n=${centroidsX.size}, " +
                "xVar=$xVariance, yVar=$yVariance, oVar=$orientationVariance",
        )

        return when {
            orientationVariance > ORIENTATION_VARIANCE_THRESHOLD -> TransferMode.KINETIC
            xVariance > POSITION_VARIANCE_THRESHOLD ||
                yVariance > POSITION_VARIANCE_THRESHOLD -> TransferMode.KINETIC
            else -> TransferMode.STATIC
        }
    }

    private fun variance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        return (values.sumOf { (it - mean) * (it - mean) } / values.size).toFloat()
    }

    companion object {
        private const val TAG = "AutoModeDetector"
        private const val MIN_SAMPLES = 3

        /**
         * A static scene has orientation variance near zero (tenths of a
         * milliradian squared). A rotating mass at 0.25 Hz over 1.5 s
         * sweeps ~2.4 rad, which produces variance orders of magnitude
         * above this threshold.
         */
        private const val ORIENTATION_VARIANCE_THRESHOLD = 0.05f
        private const val POSITION_VARIANCE_THRESHOLD = 0.01f
    }
}
