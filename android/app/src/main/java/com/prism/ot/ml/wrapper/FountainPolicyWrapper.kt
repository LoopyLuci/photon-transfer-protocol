package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M7 — Fountain Degree Optimiser.
 *
 * Predicts the optimal fountain coding degree based on current channel
 * conditions. Runs on GPU or NNAPI.
 */
@Singleton
class FountainPolicyWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M07_FOUNTAIN_POLICY) {

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        val weights = loader.readBytes(modelId)
        return object : InterpreterHandle {
            override fun close() {}
        }
    }
}
