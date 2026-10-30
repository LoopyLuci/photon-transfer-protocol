package com.prism.ot.ml.wrapper

import android.graphics.Bitmap
import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.DecodabilityScore
import com.prism.ot.ml.model.MlModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M1 — Decodability Oracle.
 *
 * Model contract:
 *  input  : float32[1, 224, 224, 3], RGB normalised to [-1, 1]
 *  outputs: [decodability_logit, blur_sigma, contrast, occlusion]
 *
 * The oracle is trained against the *actual* downstream decoder: positives
 * are frames that the classical/ZOI decoder successfully decodes, negatives
 * are frames degraded until decoding fails. It is not a generic image-quality
 * regressor.
 */
@Singleton
class DecodabilityWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M01_DECODABILITY) {

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        val file = loader.materialize(modelId)
        // In production this is a LiteRT CompiledModel. The handle is
        // intentionally opaque; see Batch 3.11's commentary.
        return LiteRtHandle(
            modelPath = file.absolutePath,
            accelerator = accelerator,
            inputShape = intArrayOf(1, 224, 224, 3),
            outputShape = intArrayOf(1, 4),
        )
    }

    suspend fun score(frame: Bitmap): DecodabilityScore {
        val handle = requireHandle() as LiteRtHandle
        val (outputs, _) = handle.runSingle(frame, modelId)
        val logit = outputs[0]
        val prob = sigmoid(logit)
        return DecodabilityScore(
            score = prob,
            blurEstimateSigmaPx = outputs[1].coerceAtLeast(0f),
            contrastEstimate = outputs[2].coerceIn(0f, 1f),
            occlusionEstimate = outputs[3].coerceIn(0f, 1f),
        )
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x))
}
