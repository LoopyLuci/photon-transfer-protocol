package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M8 — Face Tracker.
 *
 * Tracks the user's face to adjust display brightness and symbol
 * positioning for optimal decode. Runs on GPU or NNAPI.
 */
@Singleton
class FaceTrackerWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M08_FACE_TRACKER) {

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        val weights = loader.readBytes(modelId)
        return object : InterpreterHandle {
            override fun close() {}
        }
    }
}
