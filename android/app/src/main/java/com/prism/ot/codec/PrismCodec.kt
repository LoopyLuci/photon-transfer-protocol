package com.prism.ot.codec

import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.ChannelState
import com.prism.ot.data.model.DecodedTile
import com.prism.ot.data.model.FileMetadata
import com.prism.ot.data.model.FountainPacket
import com.prism.ot.data.model.TransferMode
import com.prism.ot.ml.model.SoftSymbolProbabilities

/**
 * End-to-end codec orchestrator. Owns a [CodecBridge] handle and composes:
 *
 *   File bytes
 *     → segmentation (32 MiB segments)
 *     → fountain outer code (RaptorQ or LT)
 *     → HAP-MLC partition tree
 *     → NB-LDPC inner code
 *     → CGG symbol stream
 *     → transport frames (static tiles or kinetic faces)
 *
 * The decoder runs the reverse path. Every method returns [PrismResult] so
 * callers never need try/catch.
 */
interface PrismCodec {

    // ---- Session lifecycle ----
    suspend fun createEncoderSession(
        metadata: FileMetadata,
        mode: TransferMode,
        config: SessionConfig,
    ): PrismResult<CodecSessionHandle>

    suspend fun createDecoderSession(
        config: SessionConfig,
    ): PrismResult<CodecSessionHandle>

    suspend fun closeSession(handle: CodecSessionHandle)

    // ---- Encoding ----
    /**
     * Prepare the encoder for a file. Must be called once per session.
     * Returns the number of tiles the encoder will produce (including
     * repair tiles for the target overhead).
     */
    suspend fun beginEncoding(
        handle: CodecSessionHandle,
        fileBytes: ByteArray,
        metadata: FileMetadata,
    ): PrismResult<Int>

    /**
     * Returns the next encoded tile as raw bytes that the transport layer
     * (static renderer or kinetic face renderer) will turn into pixels.
     */
    suspend fun nextEncodedTile(handle: CodecSessionHandle): PrismResult<EncodedTileBytes?>

    suspend fun isEncodingComplete(handle: CodecSessionHandle): Boolean

    // ---- Decoding ----
    /**
     * Feed a decoded tile from the transport layer into the fountain
     * decoder. Returns true when the file is fully reconstructed.
     */
    suspend fun ingestDecodedTile(
        handle: CodecSessionHandle,
        tile: DecodedTile,
    ): PrismResult<Boolean>

    suspend fun takeReconstructedFile(handle: CodecSessionHandle): PrismResult<ByteArray?>

    suspend fun decodingProgress(handle: CodecSessionHandle): DecodingProgress

    // ---- Channel-aware policy ----
    /**
     * Re-evaluates the fountain degree distribution and overhead target
     * based on the latest [ChannelState]. Called by the session when M11
     * emits a new embedding.
     */
    suspend fun adaptToChannel(
        handle: CodecSessionHandle,
        channelState: ChannelState,
    ): PrismResult<Unit>
}

/**
 * Opaque handle returned by session creation. Carries the native handle
 * plus the Kotlin-side configuration so callers can pass it around safely.
 */
data class CodecSessionHandle(
    val nativeHandle: Long,
    val config: SessionConfig,
    val mode: TransferMode,
    val metadata: FileMetadata? = null,
) {
    val isEncoder: Boolean get() = metadata != null
}

data class EncodedTileBytes(
    val tileId: Int,
    val faceId: Int?,
    val packetBytes: ByteArray,
    val symbolBytes: ByteArray,
    val isRepair: Boolean,
)

data class DecodingProgress(
    val packetsCollected: Int,
    val packetsRequired: Int,
    val segmentsComplete: Int,
    val segmentsTotal: Int,
) {
    val fractionComplete: Float
        get() = if (packetsRequired <= 0) 0f
        else (packetsCollected.toDouble() / packetsRequired.toDouble()).toFloat().coerceIn(0f, 1f)
}
