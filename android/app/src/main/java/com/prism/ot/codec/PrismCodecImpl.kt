package com.prism.ot.codec

import com.prism.ot.core.PrismConstants
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.ChannelState
import com.prism.ot.data.model.DecodedTile
import com.prism.ot.data.model.FileMetadata
import com.prism.ot.data.model.TransferMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [PrismCodec].
 *
 * The native bridge is the source of truth for fountain operations and
 * inner-code arithmetic. This class is responsible for:
 *  - segmenting the file,
 *  - serialising the metadata header,
 *  - mapping [ChannelState] to a degree-distribution selection,
 *  - translating between [DecodedTile] and native packet buffers.
 *
 * All native calls happen on the codec dispatcher (Batch 2), so this class
 * does not need its own concurrency control beyond session-handle ownership.
 */
@Singleton
class PrismCodecImpl @Inject constructor(
    private val bridge: CodecBridge,
    private val logger: PrismLogger,
) : PrismCodec {

    override suspend fun createEncoderSession(
        metadata: FileMetadata,
        mode: TransferMode,
        config: SessionConfig,
    ): PrismResult<CodecSessionHandle> = runCatching {
        val handle = bridge.createEncoder(config)
        if (handle == 0L) error("native createEncoder returned 0")
        CodecSessionHandle(handle, config, mode, metadata)
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { PrismResult.Failure(PrismError.CodecInitializationFailed(it.message ?: "unknown"), it) },
    )

    override suspend fun createDecoderSession(
        config: SessionConfig,
    ): PrismResult<CodecSessionHandle> = runCatching {
        val handle = bridge.createDecoder(config)
        if (handle == 0L) error("native createDecoder returned 0")
        CodecSessionHandle(handle, config, TransferMode.STATIC, metadata = null)
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { PrismResult.Failure(PrismError.CodecInitializationFailed(it.message ?: "unknown"), it) },
    )

    override suspend fun closeSession(handle: CodecSessionHandle) {
        if (handle.isEncoder) bridge.releaseEncoder(handle.nativeHandle)
        else bridge.releaseDecoder(handle.nativeHandle)
    }

    override suspend fun beginEncoding(
        handle: CodecSessionHandle,
        fileBytes: ByteArray,
        metadata: FileMetadata,
    ): PrismResult<Int> = runCatching {
        require(handle.isEncoder) { "beginEncoding requires an encoder session" }
        val header = serializeMetadataHeader(metadata)
        val tileCount = bridge.beginEncoding(handle.nativeHandle, fileBytes, header)
        if (tileCount <= 0) error("encoder produced no tiles")
        tileCount
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { PrismResult.Failure(PrismError.CodecInitializationFailed(it.message ?: "beginEncoding"), it) },
    )

    override suspend fun nextEncodedTile(
        handle: CodecSessionHandle,
    ): PrismResult<EncodedTileBytes?> = runCatching {
        require(handle.isEncoder) { "nextEncodedTile requires an encoder session" }
        bridge.nextEncodedTile(handle.nativeHandle)
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { PrismResult.Failure(PrismError.InvalidTile(it.message ?: "nextEncodedTile"), it) },
    )

    override suspend fun isEncodingComplete(handle: CodecSessionHandle): Boolean =
        bridge.isEncodingComplete(handle.nativeHandle)

    override suspend fun ingestDecodedTile(
        handle: CodecSessionHandle,
        tile: DecodedTile,
    ): PrismResult<Boolean> = runCatching {
        require(!handle.isEncoder) { "ingestDecodedTile requires a decoder session" }
        bridge.ingestPacket(handle.nativeHandle, tile.packetBytes)
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { PrismResult.Failure(PrismError.FountainDecodeFailed(it.message ?: "ingest"), it) },
    )

    override suspend fun takeReconstructedFile(
        handle: CodecSessionHandle,
    ): PrismResult<ByteArray?> = runCatching {
        bridge.takeReconstructedFile(handle.nativeHandle)
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { PrismResult.Failure(PrismError.FountainDecodeFailed(it.message ?: "take"), it) },
    )

    override suspend fun decodingProgress(handle: CodecSessionHandle): DecodingProgress {
        val collected = bridge.packetsCollected(handle.nativeHandle)
        val required = bridge.packetsRequired(handle.nativeHandle)
        val segmentsTotal = handle.config.segmentBytes.takeIf { it > 0 }?.let { seg ->
            // Derived from config; actual segment count is stored natively.
            1
        } ?: 1
        return DecodingProgress(
            packetsCollected = collected,
            packetsRequired = required,
            segmentsComplete = if (collected >= required) segmentsTotal else 0,
            segmentsTotal = segmentsTotal,
        )
    }

    /**
     * Channel-aware adaptation. Reads the current [ChannelState] and
     * selects a degree distribution + overhead target from the native
     * policy bank. The native bridge exposes this through a reconfiguration
     * call that is idempotent within a session.
     */
    override suspend fun adaptToChannel(
        handle: CodecSessionHandle,
        channelState: ChannelState,
    ): PrismResult<Unit> = runCatching {
        val distributionId = selectDistribution(channelState)
        val overhead = selectOverhead(channelState)
        // Native side applies the new policy to subsequent tiles.
        bridge.reconfigurePolicy(handle.nativeHandle, distributionId, overhead)
    }.fold(
        onSuccess = { PrismResult.Success(Unit) },
        onFailure = { PrismResult.Failure(PrismError.CodecInitializationFailed(it.message ?: "adapt"), it) },
    )

    /**
     * Simple deterministic policy. In production this is replaced by the
     * M7 fountain policy output; this method provides a safe fallback when
     * M7 is unavailable.
     */
    private fun selectDistribution(state: ChannelState): Int = when {
        state.erasureRate < 0.10f -> DIST_ROBUST_LOW
        state.erasureRate < 0.25f -> DIST_BALANCED
        else -> DIST_DENSE
    }

    private fun selectOverhead(state: ChannelState): Float = when {
        state.erasureRate < 0.10f -> 1.15f
        state.erasureRate < 0.25f -> 1.30f
        else -> 1.50f
    }

    private fun serializeMetadataHeader(metadata: FileMetadata): ByteArray {
        // Compact binary header. Layout is frozen for format version 1.
        // The native side parses this identically.
        val nameBytes = metadata.fileName.toByteArray(Charsets.UTF_8)
        val mimeBytes = metadata.mimeType.toByteArray(Charsets.UTF_8)
        val shaBytes = hexToBytes(metadata.sha256Hex)
        require(nameBytes.size <= 255) { "fileName too long" }
        require(mimeBytes.size <= 255) { "mimeType too long" }

        val out = java.io.ByteArrayOutputStream()
        out.write(PrismConstants.FORMAT_VERSION)
        out.write(nameBytes.size); out.write(nameBytes)
        out.write(mimeBytes.size); out.write(mimeBytes)
        out.write(shaBytes)
        out.write(longToBytes(metadata.sizeBytes))
        out.write(intToBytes(metadata.segmentSizeBytes))
        out.write(intToBytes(metadata.segmentCount))
        return out.toByteArray()
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun longToBytes(v: Long): ByteArray =
        java.nio.ByteBuffer.allocate(8).putLong(v).array()

    private fun intToBytes(v: Int): ByteArray =
        java.nio.ByteBuffer.allocate(4).putInt(v).array()

    companion object {
        // Distribution bank indices; must match native side.
        const val DIST_ROBUST_LOW = 0
        const val DIST_BALANCED = 1
        const val DIST_DENSE = 2
    }
}
