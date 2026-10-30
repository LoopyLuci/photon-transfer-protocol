package com.prism.ot.data.model

import android.net.Uri

/**
 * Final outcome of a completed (or aborted) transfer.
 */
sealed class TransferResult {

    data class Success(
        val outputUri: Uri,
        val metadata: FileMetadata,
        val elapsedMillis: Long,
        val totalBytesReceived: Long,
        val framesConsumed: Long,
        val mode: TransferMode,
    ) : TransferResult()

    data class Cancelled(
        val sessionId: String,
        val bytesReceived: Long,
        val bytesTotal: Long,
    ) : TransferResult()

    data class Failed(
        val error: com.prism.ot.core.PrismError,
        val partialBytesReceived: Long,
        val bytesTotal: Long,
    ) : TransferResult()
}
