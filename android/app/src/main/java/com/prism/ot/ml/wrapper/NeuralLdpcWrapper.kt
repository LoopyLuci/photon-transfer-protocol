package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.ml.model.SoftSymbolProbabilities
import com.prism.ot.ml.zoi.ZoiLdpcNative
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M6 — Neural Inner Decoder (ZOI).
 *
 * Root-layer blocks (file metadata, RFL level 0) use the message-passing
 * transformer path; all payload tiles use boosted NMS.
 */
@Singleton
class NeuralLdpcWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M06_NEURAL_LDPC) {

    @Volatile private var native: ZoiLdpcNative? = null

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        require(accelerator == Accelerator.ZERO_OVERHEAD_NATIVE) {
            "M6 only supports ZOI"
        }
        val params = loader.readBytes(modelId)
        val instance = ZoiLdpcNative.load(params)
        native = instance
        return object : InterpreterHandle {
            override fun close() = instance.close()
        }
    }

    suspend fun decode(
        softSymbols: SoftSymbolProbabilities,
        parityCheckMatrixId: Int,
    ): ByteArray {
        val n = requireNotNull(native) { "M6 not initialised" }
        // Parity-check matrix 0 is reserved for the root layer; it uses the
        // transformer decoder. All other matrices use boosted NMS.
        return if (parityCheckMatrixId == ROOT_PCM_ID) {
            n.messagePassingTransformerDecode(softSymbols, parityCheckMatrixId)
        } else {
            n.boostedNmsDecode(softSymbols, parityCheckMatrixId)
        }
    }

    companion object {
        const val ROOT_PCM_ID: Int = 0
    }
}
