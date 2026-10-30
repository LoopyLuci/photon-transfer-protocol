package com.prism.ot.motor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces the kinematic rule that makes the rotating mass decodable by the
 * slowest supported camera.
 *
 * With F faces and a revolution period T, the face presentation rate is
 *
 *     R_face = F / T
 *
 * To guarantee that an R_camera camera captures every face exactly once
 * per revolution, we require
 *
 *     R_face <= R_camera * SAFETY_MARGIN
 *
 * The safety margin (0.85) accounts for jitter in the measured camera FPS
 * and for the finite exposure window. At 8 fps, 16 faces and a 0.85 margin,
 * the maximum rotation frequency is:
 *
 *     F / (T) <= 8 * 0.85 = 6.8 faces/s
 *     T >= 16 / 6.8 = 2.35 s
 *     f_rotation <= 1 / 2.35 = 0.425 Hz
 *
 * This is deliberately slower than the 0.5 Hz that a naive F/T = R_camera
 * rule would allow, because real cameras drop frames under load and the
 * fountain decoder should never be the only thing standing between a user
 * and a failed transfer.
 */
@Singleton
class FaceRateMatcher @Inject constructor() {

    /**
     * Maximum safe rotation frequency in Hz for the given face count and
     * measured camera frame rate.
     */
    fun maxSafeRotationHz(
        faceCount: Int,
        cameraFps: Float,
        safetyMargin: Float = SAFETY_MARGIN,
    ): Float {
        require(faceCount > 0) { "faceCount must be positive" }
        if (cameraFps <= 0f) return MIN_ROTATION_HZ
        val maxFaceRate = cameraFps * safetyMargin
        val maxPeriodSeconds = faceCount / maxFaceRate
        return (1f / maxPeriodSeconds).coerceIn(MIN_ROTATION_HZ, MAX_ROTATION_HZ)
    }

    /**
     * Clamps a requested rotation frequency to the safe range for the
     * current camera and face count. Returns the clamped value and the
     * reason it was clamped, so the UI can explain why the mass slowed down.
     */
    fun clamp(
        requestedHz: Float,
        faceCount: Int,
        cameraFps: Float,
    ): ClampResult {
        val maxHz = maxSafeRotationHz(faceCount, cameraFps)
        return when {
            requestedHz <= 0f -> ClampResult(MIN_ROTATION_HZ, ClampReason.BELOW_MINIMUM)
            requestedHz > maxHz -> ClampResult(maxHz, ClampReason.CAMERA_FPS_LIMIT)
            requestedHz < MIN_ROTATION_HZ -> ClampResult(MIN_ROTATION_HZ, ClampReason.BELOW_MINIMUM)
            else -> ClampResult(requestedHz, ClampReason.NONE)
        }
    }

    /**
     * Predicts throughput in bytes per second for a given configuration.
     * Used by the UI to show the user the trade-off before they start.
     */
    fun predictedThroughputBps(
        faceCount: Int,
        faceBytes: Int,
        cameraFps: Float,
    ): Long {
        val effectiveFaceRate = minOf(faceCount / (1f / maxSafeRotationHz(faceCount, cameraFps)), cameraFps)
        return (effectiveFaceRate * faceBytes).toLong()
    }

    data class ClampResult(val hz: Float, val reason: ClampReason)

    enum class ClampReason {
        NONE,
        CAMERA_FPS_LIMIT,
        BELOW_MINIMUM,
    }

    companion object {
        const val SAFETY_MARGIN = 0.85f
        const val MIN_ROTATION_HZ = 0.05f
        const val MAX_ROTATION_HZ = 2.0f
    }
}
