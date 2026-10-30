package com.prism.ot.transfer

import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.DecodedTile
import com.prism.ot.data.model.FileMetadata
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.model.TransferProgress
import com.prism.ot.data.model.TransferResult
import kotlinx.coroutines.flow.SharedFlow

/**
 * A single in-flight transfer. Owns:
 *  - one [com.prism.ot.codec.CodecSessionHandle] (decoder side),
 *  - the accumulation of [DecodedTile]s into fountain packets,
 *  - a throttled progress stream,
 *  - the final reconstruction and SHA-256 verification.
 *
 * Lifecycle is explicit: [start] → repeated [ingestTile] → [finalize] or
 * [cancel]. Sessions are single-shot; create a new one per transfer.
 */
interface TransferSession {

    val sessionId: String
    val mode: TransferMode
    val metadata: FileMetadata

    /**
     * Hot stream of progress snapshots. Emits at most once per
     * [com.prism.ot.core.PrismConstants.PROGRESS_EMIT_INTERVAL_MS], and
     * always emits a final snapshot with fraction 1.0 or the failure state.
     */
    val progress: SharedFlow<TransferProgress>

    /** One-shot terminal result, completes when the session ends. */
    val result: SharedFlow<TransferResult>

    suspend fun start(): PrismResult<Unit>

    /**
     * Feed a tile decoded by the transport layer. Safe to call
     * concurrently from multiple camera analysis threads; the
     * implementation serialises ingestion onto the codec dispatcher.
     *
     * Returns true if this tile completed the file and [finalize] should
     * be called.
     */
    suspend fun ingestTile(tile: DecodedTile): PrismResult<Boolean>

    /**
     * Reconstruct the file, verify SHA-256, and publish it to
     * [com.prism.ot.io.ReceivedFileStore]. Emits [TransferResult] on
     * [result] when done.
     */
    suspend fun finalize(): PrismResult<TransferResult>

    /** Cancel the session. Emits [TransferResult.Cancelled] on [result]. */
    suspend fun cancel(reason: String = "user")
}
