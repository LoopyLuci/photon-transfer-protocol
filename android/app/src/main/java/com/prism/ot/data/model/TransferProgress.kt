package com.prism.ot.data.model

/**
 * Snapshot of an in-flight transfer. Emitted to the UI at
 * [com.prism.ot.core.PrismConstants.PROGRESS_EMIT_INTERVAL_MS] intervals.
 *
 * Never emit per-frame; batch aggregate updates to preserve battery.
 */
data class TransferProgress(
    val sessionId: String,
    val mode: TransferMode,
    val bytesReceived: Long,
    val bytesTotal: Long,
    val packetsCollected: Int,
    val packetsRequired: Int,
    val framesProcessed: Long,
    val framesDecoded: Long,
    val tilesRejected: Long,
    val elapsedMillis: Long,
    val currentThroughputBps: Long,
    val channelState: ChannelState,
) {
    val fractionComplete: Float
        get() = if (bytesTotal <= 0L) 0f
        else (bytesReceived.toDouble() / bytesTotal.toDouble()).toFloat().coerceIn(0f, 1f)

    val decodeSuccessRate: Float
        get() = if (framesProcessed == 0L) 0f
        else (framesDecoded.toDouble() / framesProcessed.toDouble()).toFloat()

    val etaMillis: Long
        get() {
            if (currentThroughputBps <= 0L) return -1L
            val remaining = (bytesTotal - bytesReceived).coerceAtLeast(0L)
            return (remaining * 1000L) / currentThroughputBps
        }
}
