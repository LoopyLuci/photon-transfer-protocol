package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M13 — Synthetic Channel Generator.
 *
 * Generates synthetic channel conditions for testing and simulation.
 * Runs on GPU or NNAPI.
 */
@Singleton
class SyntheticChannelWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M13_CHANNEL_GENERATOR) {

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        val weights = loader.readBytes(modelId)
        return object : InterpreterHandle {
            override fun close() {}
        }
    }
}
