package com.prism.ot.capture

/**
 * Sliding-window frame-rate estimator.
 *
 * Uses sensor timestamps (`ImageInfo.getTimestamp()`) rather than wall-clock
 * time, because the sensor timestamp reflects the start of exposure and is
 * not affected by scheduling jitter in the analyzer executor.
 *
 * Not thread-safe. It is owned by the analyzer thread.
 */
class CameraFpsMonitor(private val window: Int = 32) {

    private val timestamps = LongArray(window)
    private var writeIndex = 0
    private var count = 0

    fun onFrame(sensorTimestampNanos: Long): Float {
        timestamps[writeIndex] = sensorTimestampNanos
        writeIndex = (writeIndex + 1) % window
        if (count < window) count++
        return currentFps()
    }

    fun currentFps(): Float {
        if (count < 2) return 0f
        val oldestIndex = if (count < window) 0 else writeIndex
        val newestIndex = if (writeIndex == 0) window - 1 else writeIndex - 1
        val first = timestamps[oldestIndex]
        val last = timestamps[newestIndex]
        if (last <= first) return 0f
        val seconds = (last - first) / 1_000_000_000.0
        return ((count - 1) / seconds).toFloat()
    }
}
