package com.prism.ot.ml.zoi

import android.graphics.Bitmap
import com.prism.ot.ml.KeepForJni
import com.prism.ot.ml.model.SoftSymbolProbabilities

/**
 * Zero-overhead inference for M5 (Shape–Colour Classifier).
 *
 * The ZOI path loads INT8 weights and biases once into native memory and
 * executes an optimised matrix–multiply pipeline in Rust. No LiteRT or
 * TFLite runtime is linked at inference time, which is the key enabler for
 * low-end and older devices where runtime initialisation and memory
 * footprint dominate.
 *
 * The Kotlin side only prepares the input tensor (rectified tile + colour
 * transform already applied) and post-processes the softmax output.
 */
@KeepForJni
class ZoiClassifierNative private constructor(
    private val handle: Long,
    val alphabetSize: Int,
) : AutoCloseable {

    /**
     * Returns a probability vector of length [alphabetSize]. The native
     * implementation applies temperature scaling internally using the
     * calibrated temperature passed at load time.
     */
    fun forward(input: FloatArray): FloatArray = nativeForward(handle, input)

    fun forwardBitmap(
        rectifiedTile: Bitmap,
        outputWidth: Int = 32,
        outputHeight: Int = 32,
    ): SoftSymbolProbabilities {
        val pixels = IntArray(outputWidth * outputHeight)
        val scaled = Bitmap.createScaledBitmap(rectifiedTile, outputWidth, outputHeight, true)
        scaled.getPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        if (scaled !== rectifiedTile) scaled.recycle()

        val input = FloatArray(outputWidth * outputHeight * 3)
        var idx = 0
        for (p in pixels) {
            input[idx++] = ((p shr 16) and 0xFF) / 255f
            input[idx++] = ((p shr 8) and 0xFF) / 255f
            input[idx++] = (p and 0xFF) / 255f
        }
        val probs = forward(input)
        return SoftSymbolProbabilities(
            probabilities = probs,
            alphabetSize = probs.size,
            entropy = normalisedEntropy(probs),
            topSymbolIndex = probs.indices.maxByOrNull { probs[it] } ?: 0,
            topSymbolConfidence = probs.maxOrNull() ?: 0f,
            classificationTimeMicros = 0L,
        )
    }

    override fun close() {
        nativeRelease(handle)
    }

    private fun normalisedEntropy(p: FloatArray): Float {
        var h = 0.0
        for (v in p) if (v > 1e-9f) h -= v * kotlin.math.ln(v.toDouble())
        val maxH = kotlin.math.ln(p.size.toDouble())
        return if (maxH <= 0.0) 0f else (h / maxH).toFloat().coerceIn(0f, 1f)
    }

    private external fun nativeForward(handle: Long, input: FloatArray): FloatArray
    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("prism_core")
        }

        /**
         * Loads INT8 weights from [weights] (a flat little-endian INT8 buffer
         * with a 16-byte header describing shape and temperature).
         */
        fun load(
            weights: ByteArray,
            alphabetSize: Int,
            temperature: Float,
        ): ZoiClassifierNative {
            val handle = nativeCreate(weights, alphabetSize, temperature)
            require(handle != 0L) { "ZOI classifier handle creation failed" }
            return ZoiClassifierNative(handle, alphabetSize)
        }

        @JvmStatic
        private external fun nativeCreate(
            weights: ByteArray,
            alphabetSize: Int,
            temperature: Float,
        ): Long
    }
}
