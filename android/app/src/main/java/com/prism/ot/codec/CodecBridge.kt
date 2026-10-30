package com.prism.ot.codec

import com.prism.ot.data.model.FountainPacket
import com.prism.ot.ml.model.SoftSymbolProbabilities

/**
 * Thin JNI surface into the Rust codec core (`libprism_core.so`).
 *
 * Every method here has a 1:1 counterpart in `core/src/jni/codec.rs`. The
 * contract is intentionally minimal: Kotlin does not know about Spectre
 * tiles, GF(64) arithmetic, or RaptorQ precode matrices. It only knows
 * opaque handles and byte arrays.
 *
 * Lifecycle:
 *  - [createEncoder] returns a handle that must be released with
 *    [releaseEncoder] when the session ends.
 *  - [createDecoder] returns a handle that must be released with
 *    [releaseDecoder].
 *  - Handles are **not** thread-safe. Callers must serialise access per
 *    handle (the codec dispatcher in Batch 2 provides this).
 */
interface CodecBridge {

    // ---- Lifecycle ----
    fun nativeVersion(): String
    fun createEncoder(sessionConfig: SessionConfig): Long
    fun releaseEncoder(handle: Long)
    fun createDecoder(sessionConfig: SessionConfig): Long
    fun releaseDecoder(handle: Long)

    // ---- Encoding (sender side) ----
    /**
     * Encode [fileBytes] into a stream of fountain-coded tiles.
     * Returns tile count; tiles are retrieved with [nextEncodedTile].
     * Call repeatedly until [isEncodingComplete] returns true.
     */
    fun beginEncoding(handle: Long, fileBytes: ByteArray, metadataHeader: ByteArray): Int
    fun nextEncodedTile(handle: Long): EncodedTileBytes?
    fun isEncodingComplete(handle: Long): Boolean

    // ---- Decoding (receiver side) ----
    /**
     * Feed a decoded tile's packet bytes into the fountain decoder.
     * Returns true if the file has been fully reconstructed and is
     * available via [takeReconstructedFile].
     */
    fun ingestPacket(handle: Long, packetBytes: ByteArray): Boolean
    fun takeReconstructedFile(handle: Long): ByteArray?
    fun packetsCollected(handle: Long): Int
    fun packetsRequired(handle: Long): Int

    // ---- Inner code (NB-LDPC) ----
    /**
     * Encode a fountain packet into GF(q) symbols using the HAP-MLC
     * partition tree. Returns a flat array of symbol indices.
     */
    fun encodeSymbols(handle: Long, packetBytes: ByteArray): IntArray

    /**
     * Decode soft symbol probabilities into packet bytes using the
     * configured NB-LDPC parity-check matrix.
     */
    fun decodeSymbols(handle: Long, softSymbols: SoftSymbolProbabilities): ByteArray

    // ---- RaptorQ specific ----
    /**
     * Returns the RaptorQ source-block parameters for a given payload size.
     * The Kotlin side uses this to size segments and compute overhead.
     */
    fun raptorQParams(payloadBytes: Long, symbolBytes: Int): RaptorQParams

    // ---- Diagnostics ----
    fun lastErrorCode(handle: Long): Int
    fun lastErrorMessage(handle: Long): String
}
