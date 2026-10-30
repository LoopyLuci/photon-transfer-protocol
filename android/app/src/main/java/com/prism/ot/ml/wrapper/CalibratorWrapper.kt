package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ChromaticCalibration
import com.prism.ot.ml.ChromaticPilot
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M4 — Chromatic Calibrator.
 *
 * Runs as a zero-overhead native MLP. The input is the received CIELAB
 * coordinates of the pilot patches; the output is a 3×4 affine transform
 * plus the residual ΔE00.
 *
 * Training is unsupervised: minimise distance between transformed pilots
 * and the nearest alphabet point, with a smoothness regulariser. This
 * avoids needing labelled data for every lighting condition.
 */
@Singleton
class CalibratorWrapper @Inject constructor(
    loader: ModelLoader,
    logger: PrismLogger,
) : BaseModelWrapper<Any>(loader, logger, MlModelId.M04_CALIBRATOR) {

    private var native: com.prism.ot.ml.zoi.ZoiCalibratorNative? = null

    override suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle {
        require(accelerator == Accelerator.ZERO_OVERHEAD_NATIVE) {
            "M4 only supports ZOI"
        }
        val weights = loader.readBytes(modelId)
        val instance = com.prism.ot.ml.zoi.ZoiCalibratorNative.load(weights)
        native = instance
        return object : InterpreterHandle {
            override fun close() = instance.close()
        }
    }

    suspend fun calibrate(pilots: List<ChromaticPilot>): ChromaticCalibration {
        val n = requireNotNull(native) { "M4 not initialised" }
        val packed = FloatArray(pilots.size * 6)
        var i = 0
        for (p in pilots) {
            packed[i++] = p.intendedL
            packed[i++] = p.intendedA
            packed[i++] = p.intendedB
            packed[i++] = p.receivedR / 255f
            packed[i++] = p.receivedG / 255f
            packed[i++] = p.receivedB / 255f
        }
        val result = n.calibrate(packed, pilots.size)
        return ChromaticCalibration(
            transformMatrix = result.copyOfRange(0, 12),
            residualDeltaE00 = result[12],
            activeColorCount = result[13].toInt(),
        )
    }
}
