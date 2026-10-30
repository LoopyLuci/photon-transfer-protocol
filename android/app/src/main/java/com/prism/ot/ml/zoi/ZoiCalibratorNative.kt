package com.prism.ot.ml.zoi

import com.prism.ot.ml.KeepForJni

/**
 * Zero-overhead inference for M4 (Chromatic Calibrator).
 *
 * The calibrator is a 3-layer MLP that maps pilot CIELAB coordinates to an
 * affine transform. It is tiny (< 20 KB) and must run in under 1 ms per
 * frame, so it is implemented natively.
 */
@KeepForJni
class ZoiCalibratorNative private constructor(private val handle: Long) : AutoCloseable {

    /**
     * @param packedPilots length = pilotCount × 6, layout
     *        [L, a, b, r, g, b, L, a, b, r, g, b, ...]
     * @return length 14: 12 affine coefficients, residual ΔE00, activeColourCount
     */
    fun calibrate(packedPilots: FloatArray, pilotCount: Int): FloatArray =
        nativeCalibrate(handle, packedPilots, pilotCount)

    override fun close() {
        nativeRelease(handle)
    }

    private external fun nativeCalibrate(
        handle: Long,
        packedPilots: FloatArray,
        pilotCount: Int,
    ): FloatArray

    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("prism_core")
        }

        fun load(weights: ByteArray): ZoiCalibratorNative {
            val handle = nativeCreate(weights)
            require(handle != 0L) { "ZOI calibrator handle creation failed" }
            return ZoiCalibratorNative(handle)
        }

        @JvmStatic
        private external fun nativeCreate(weights: ByteArray): Long
    }
}
