package com.prism.ot.data.model

/**
 * Discrete snapshot of the optical channel, produced by the M11 Channel State
 * Transformer and fed into M3, M6, M7, M10, and M1. Immutable so it can be
 * passed across coroutine boundaries without locks.
 */
data class ChannelState(
    /** Estimated Gaussian blur sigma in pixels on the analysis frame. */
    val blurSigmaPx: Float = 0f,
    /** Mean ΔE00 residual between pilots and intended CIELAB coordinates. */
    val colorShiftDelta: Float = 0f,
    /** Estimated perspective tilt angle in degrees. */
    val perspectiveAngleDeg: Float = 0f,
    /** Approximate signal-to-noise ratio in dB. */
    val snrDb: Float = 0f,
    /** Sliding-window erasure rate [0,1]. */
    val erasureRate: Float = 0f,
    /** Recent decode success ratio [0,1] over the last window. */
    val recentDecodeRate: Float = 0f,
    /** Estimated current frame rate of the capture pipeline. */
    val captureFps: Float = 0f,
    /** Normalised reliability [0,1]; 1.0 = pristine. */
    val overallQuality: Float = 0f,
) {
    val isDegraded: Boolean
        get() = blurSigmaPx > 2.5f ||
            colorShiftDelta > 8f ||
            erasureRate > 0.35f ||
            recentDecodeRate < 0.4f

    companion object {
        val UNKNOWN = ChannelState()

        fun fromQuality(quality: Float): ChannelState =
            ChannelState(overallQuality = quality.coerceIn(0f, 1f))
    }
}
