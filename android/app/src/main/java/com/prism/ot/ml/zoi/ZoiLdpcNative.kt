package com.prism.ot.ml.zoi

import com.prism.ot.ml.KeepForJni
import com.prism.ot.ml.model.SoftSymbolProbabilities

/**
 * Zero-overhead inference for M6 (Neural Inner Decoder).
 *
 * Two native kernels are exposed:
 *  - [boostedNmsDecode] for bulk inner-code decoding using learned
 *    min-sum offset/slope parameters.
 *  - [messagePassingTransformerDecode] for short, critical root-layer blocks.
 *
 * Both accept soft symbol probabilities from M5 and return corrected bytes.
 * The parity-check matrix is selected by [parityCheckMatrixId] and must have
 * been pre-loaded into the native core by [PrismCodec] during session setup.
 */
@KeepForJni
class ZoiLdpcNative private constructor(private val handle: Long) : AutoCloseable {

    /**
     * Boosted neural min-sum decoder. Fast, low memory, suitable for every
     * payload tile.
     */
    fun boostedNmsDecode(
        softSymbols: SoftSymbolProbabilities,
        parityCheckMatrixId: Int,
        maxIterations: Int = 32,
    ): ByteArray = nativeBoostedNms(
        handle,
        softSymbols.probabilities,
        parityCheckMatrixId,
        maxIterations,
    )

    /**
     * Message-passing transformer decoder. Higher latency, used only for
     * RFL root-level blocks that carry file metadata.
     */
    fun messagePassingTransformerDecode(
        softSymbols: SoftSymbolProbabilities,
        parityCheckMatrixId: Int,
        maxIterations: Int = 16,
    ): ByteArray = nativeMessagePassingTransformer(
        handle,
        softSymbols.probabilities,
        parityCheckMatrixId,
        maxIterations,
    )

    override fun close() {
        nativeRelease(handle)
    }

    private external fun nativeBoostedNms(
        handle: Long,
        probabilities: FloatArray,
        parityCheckMatrixId: Int,
        maxIterations: Int,
    ): ByteArray

    private external fun nativeMessagePassingTransformer(
        handle: Long,
        probabilities: FloatArray,
        parityCheckMatrixId: Int,
        maxIterations: Int,
    ): ByteArray

    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("prism_core")
        }

        fun load(learnedParameters: ByteArray): ZoiLdpcNative {
            val handle = nativeCreate(learnedParameters)
            require(handle != 0L) { "ZOI LDPC handle creation failed" }
            return ZoiLdpcNative(handle)
        }

        @JvmStatic
        private external fun nativeCreate(learnedParameters: ByteArray): Long
    }
}
