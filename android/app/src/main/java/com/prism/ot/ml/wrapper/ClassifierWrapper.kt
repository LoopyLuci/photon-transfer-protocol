package com.prism.ot.ml.wrapper

import android.graphics.Bitmap
import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ChromaticCalibration
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.ml.model.SoftSymbolProbabilities
import com.prism.ot.ml.zoi.ZoiClassifierNative
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M5 — Shape–Colour Classifier (ZOI).
 *
 * This is the hottest model in the pipeline: called once per tile and
 * therefore once every few milliseconds during scanning. It runs entirely
 * through [ZoiClassifierNative], avoiding any LiteRT runtime overhead.
 *
 * The classifier receives the rectified tile *after* the M4 calibration
 * has been applied. It returns calibrated soft probabilities with the
 * temperature from [PrismConstants.MODEL_TEMPERATURE].
 */
@Singleton
class ClassifierWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M05_CLASSIFIER) {

    @Volatile private var native: ZoiClassifierNative? = null

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        require(accelerator == Accelerator.ZERO_OVERHEAD_NATIVE) {
            "M5 only supports ZOI"
        }
        val weights = loader.readBytes(modelId)
        val instance = ZoiClassifierNative.load(
            weights = weights,
            alphabetSize = com.prism.ot.core.PrismConstants.SYMBOL_ALPHABET_SIZE_DENSE,
            temperature = com.prism.ot.core.PrismConstants.MODEL_TEMPERATURE,
        )
        native = instance
        return object : InterpreterHandle {
            override fun close() = instance.close()
        }
    }

    suspend fun classify(
        rectifiedTile: Bitmap,
        calibration: ChromaticCalibration,
    ): SoftSymbolProbabilities {
        val n = requireNotNull(native) { "M5 not initialised" }
        // The native classifier already applies the colour transform
        // internally when the calibration is supplied; see the Rust
        // `apply_cielab_transform` path in Batch 4.
        val raw = n.forwardBitmap(rectifiedTile)
        return raw
    }
}
