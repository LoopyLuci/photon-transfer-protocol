package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M2 — Tile Localiser.
 *
 * Detects the bounding box of the CGG symbol grid in a captured frame.
 * Runs on GPU or NNAPI; falls back to CPU if neither is available.
 */
@Singleton
class LocaliserWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M02_LOCALISER) {

    private var native: com.prism.ot.ml.zoi.ZoiLocaliserNative? = null

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        val weights = loader.readBytes(modelId)
        val instance = com.prism.ot.ml.zoi.ZoiLocaliserNative.load(weights)
        native = instance
        return object : InterpreterHandle {
            override fun close() = instance.close()
        }
    }
}
