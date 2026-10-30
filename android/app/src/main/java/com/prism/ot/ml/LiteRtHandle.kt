package com.prism.ot.ml

import android.graphics.Bitmap
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Thin adapter over LiteRT / TFLite. Kept in one place so the rest of the
 * codebase is runtime-agnostic.
 *
 * Accelerator ladder enforcement:
 *  - GPU delegate when [Accelerator.GPU] is requested *and* the device is
 *    reported compatible by [CompatibilityList].
 *  - XNNPACK CPU otherwise.
 *  - NNAPI is deliberately not offered; it is deprecated as of Android 15
 *    and the GPU + XNNPACK path is the supported migration target.
 */
internal class LiteRtHandle(
    private val modelPath: String,
    private val accelerator: Accelerator,
    private val inputShape: IntArray,
    private val outputShape: IntArray,
) : AutoCloseable {

    private val interpreter: Interpreter
    private val gpuDelegate: GpuDelegate?
    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer

    init {
        val options = Interpreter.Options()
        var gpu: GpuDelegate? = null
        when (accelerator) {
            Accelerator.GPU -> {
                require(CompatibilityList().isDelegateSupportedOnThisDevice) {
                    "GPU delegate not supported on this device"
                }
                gpu = GpuDelegate(
                    GpuDelegate.Options().apply {
                        setPrecisionLossAllowed(true)
                        setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                    }
                )
                options.addDelegate(gpu)
                options.setNumThreads(1)
            }
            Accelerator.CPU_XNNPACK -> {
                options.setUseXNNPACK(true)
                options.setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            }
            else -> error("Unsupported accelerator for LiteRT: $accelerator")
        }
        gpuDelegate = gpu
        interpreter = Interpreter(File(modelPath), options)

        val inputBytes = inputShape.fold(1L) { acc, d -> acc * d } * 4L
        val outputBytes = outputShape.fold(1L) { acc, d -> acc * d } * 4L
        inputBuffer = ByteBuffer.allocateDirect(inputBytes.toInt()).order(ByteOrder.nativeOrder())
        outputBuffer = ByteBuffer.allocateDirect(outputBytes.toInt()).order(ByteOrder.nativeOrder())

        // Warm-up: compile GPU shaders now rather than during the first
        // user-visible inference.
        if (accelerator == Accelerator.GPU) {
            runCatching { interpreter.run(inputBuffer, outputBuffer) }
            inputBuffer.rewind()
            outputBuffer.rewind()
        }
    }

    fun runSingle(bitmap: Bitmap, modelId: MlModelId): Pair<FloatArray, Long> {
        val start = System.nanoTime()
        preprocessBitmap(bitmap, inputBuffer, inputShape)
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val out = FloatArray(outputBuffer.remaining() / 4) { outputBuffer.float }
        inputBuffer.rewind()
        return out to (System.nanoTime() - start) / 1_000
    }

    override fun close() {
        runCatching { interpreter.close() }
        runCatching { gpuDelegate?.close() }
    }

    private fun preprocessBitmap(bitmap: Bitmap, dst: ByteBuffer, shape: IntArray) {
        val h = shape[1]
        val w = shape[2]
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()
        dst.rewind()
        for (p in pixels) {
            dst.putFloat((((p shr 16) and 0xFF) / 127.5f) - 1f)
            dst.putFloat((((p shr 8) and 0xFF) / 127.5f) - 1f)
            dst.putFloat(((p and 0xFF) / 127.5f) - 1f)
        }
        dst.rewind()
    }
}
