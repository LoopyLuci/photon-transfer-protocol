package com.prism.ot.ml.zoi

import com.prism.ot.ml.KeepForJni

/**
 * Zero-overhead inference for M10 (Motor Synchroniser).
 *
 * The motor policy is a tiny MLP (typically 8–16 hidden units). It is
 * intentionally run natively because motor control loops must complete in
 * well under 1 ms and must not share a thread pool with UI or camera work.
 */
@KeepForJni
class ZoiMotorPolicyNative private constructor(private val handle: Long) : AutoCloseable {

    /**
     * Returns the suggested rotation frequency in Hz.
     *
     * @param cameraFps          measured capture rate of the receiving device
     * @param decodeRate         recent successful face-decode ratio [0,1]
     * @param blurSigmaPx        current blur estimate on visible faces
     * @param currentRotationHz  current commanded rotation
     */
    fun suggest(
        cameraFps: Float,
        decodeRate: Float,
        blurSigmaPx: Float,
        currentRotationHz: Float,
    ): Float = nativeSuggest(handle, cameraFps, decodeRate, blurSigmaPx, currentRotationHz)

    override fun close() {
        nativeRelease(handle)
    }

    private external fun nativeSuggest(
        handle: Long,
        cameraFps: Float,
        decodeRate: Float,
        blurSigmaPx: Float,
        currentRotationHz: Float,
    ): Float

    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("prism_core")
        }

        fun load(weights: ByteArray): ZoiMotorPolicyNative {
            val handle = nativeCreate(weights)
            require(handle != 0L) { "ZOI motor policy handle creation failed" }
            return ZoiMotorPolicyNative(handle)
        }

        @JvmStatic
        private external fun nativeCreate(weights: ByteArray): Long
    }
}
