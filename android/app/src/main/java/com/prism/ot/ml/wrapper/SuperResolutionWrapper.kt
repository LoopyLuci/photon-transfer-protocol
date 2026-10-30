package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M3 — Super-Resolution Restorer.
 *
 * Upscales low-resolution CGG symbols to improve decode accuracy.
 * Runs on GPU or NNAPI.
 */
@Singleton
class SuperResolutionWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M03_SUPER_RESOLUTION) {

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        val weights = loader.readBytes(modelId)
        return object : InterpreterHandle {
            override fun close() {}
        }
    }
}
