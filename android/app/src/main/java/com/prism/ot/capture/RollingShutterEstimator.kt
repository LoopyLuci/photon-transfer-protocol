package com.prism.ot.capture

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts sensor readout skew into a per-frame usability decision.
 *
 * Rolling-shutter skew is the time between the first row's exposure start
 * and the last row's exposure start. When the scene moves
 * during that interval, rows are captured at different scene times. For a
 * rotating kinetic mass, this produces a shear that the tile decoder cannot
 * undo unless the skew is small relative to the tile's angular size.
 *
 * The estimator does not attempt to correct skew. It decides whether the
 * frame is worth sending to the expensive decode path. Correction, when
 * needed, is delegated to M9 (motion deblurrer), which receives the skew
 * estimate through the channel state.
 */
@Singleton
class RollingShutterEstimator @Inject constructor() {

    /**
     * @param skewNanos sensor readout skew from
     *        `CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW`
     * @param angularVelocityRadPerSec estimated mass angular velocity from M8
     * @param tileAngularSizeRad angular width of one face as seen from the
     *        camera; used to bound the acceptable shear
     * @return true if the frame's shear is below [MAX_SHEAR_FRACTION] of a
     *         tile width
     */
    fun isFrameUsable(
        skewNanos: Long,
        angularVelocityRadPerSec: Float,
        tileAngularSizeRad: Float,
    ): Boolean {
        if (skewNanos <= 0L) return true
        if (angularVelocityRadPerSec <= 0f) return true
        if (tileAngularSizeRad <= 0f) return true
        val skewSeconds = skewNanos / 1_000_000_000f
        val shearRad = angularVelocityRadPerSec * skewSeconds
        return shearRad < tileAngularSizeRad * MAX_SHEAR_FRACTION
    }

    /**
     * Expected exposure-start offset for a row at [normalizedY] in [0,1].
     * Used by M9 to condition its deblurring kernel.
     */
    fun skewAtRowNanos(skewNanos: Long, normalizedY: Float): Long =
        (skewNanos * normalizedY.coerceIn(0f, 1f)).toLong()

    companion object {
        /**
         * A shear of 25% of a tile width is the point at which the
         * classifier's soft probabilities begin to collapse. This is
         * deliberately conservative; M9 can recover moderate shear, but
         * the decodability oracle should not spend compute on frames that
         * are already lost.
         */
        const val MAX_SHEAR_FRACTION = 0.25f
    }
}
