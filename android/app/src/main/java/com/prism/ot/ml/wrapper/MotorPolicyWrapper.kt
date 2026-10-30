package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.ml.zoi.ZoiMotorPolicyNative
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M10 — Motor Synchroniser (ZOI).
 *
 * Called by the BLE motor controller at 5–20 Hz. Must not allocate or
 * touch the LiteRT runtime. The native MLP weights are < 8 KB.
 */
@Singleton
class MotorPolicyWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M10_MOTOR_POLICY) {

    @Volatile private var native: ZoiMotorPolicyNative? = null

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        require(accelerator == Accelerator.ZERO_OVERHEAD_NATIVE) {
            "M10 only supports ZOI"
        }
        val weights = loader.readBytes(modelId)
        val instance = ZoiMotorPolicyNative.load(weights)
        native = instance
        return object : InterpreterHandle {
            override fun close() = instance.close()
        }
    }

    suspend fun suggest(
        cameraFps: Float,
        decodeRate: Float,
        blurSigmaPx: Float,
    ): Float {
        val n = requireNotNull(native) { "M10 not initialised" }
        return n.suggest(
            cameraFps = cameraFps,
            decodeRate = decodeRate,
            blurSigmaPx = blurSigmaPx,
            currentRotationHz = 0f, // caller supplies current via a separate setter in production
        )
    }
}
