package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M11 — Channel State Transformer.
 *
 * Transforms channel state information into a format suitable for the
 * fountain policy. Runs on GPU or NNAPI.
 */
@Singleton
class ChannelTransformerWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M11_CHANNEL_TRANSFORMER) {

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        val weights = loader.readBytes(modelId)
        return object : InterpreterHandle {
            override fun close() {}
        }
    }
}
