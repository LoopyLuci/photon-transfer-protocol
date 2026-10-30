package com.prism.ot.codec

import com.prism.ot.core.PrismLogger
import com.prism.ot.data.model.FountainPacket
import com.prism.ot.ml.model.SoftSymbolProbabilities
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI implementation of [CodecBridge].
 *
 * Every `external` function here has a matching `#[no_mangle] pub extern
 * "system" fn Java_com_prism_ot_codec_CodecBridgeImpl_...` in
 * `core/src/jni/codec.rs`. The native side owns all handles and must free
 * them on release.
 *
 * Memory contract:
 *  - Byte arrays are copied across the JNI boundary (Rust reads
 *    `JByteArray` into a `Vec<u8>`). Handles are raw pointers cast to
 *    `jlong`; they are **not** GC-managed, so callers must release them.
 *  - Null returns from `nextEncodedTile` and `takeReconstructedFile`
 *    indicate "no more data" or "not yet complete", not an error.
 */
@Singleton
class CodecBridgeImpl @Inject constructor(
    private val logger: PrismLogger,
) : CodecBridge {

    init {
        System.loadLibrary("prism_core")
    }

    // ---- Lifecycle ----
    override external fun nativeVersion(): String
    override external fun createEncoder(sessionConfig: SessionConfig): Long
    override external fun releaseEncoder(handle: Long)
    override external fun createDecoder(sessionConfig: SessionConfig): Long
    override external fun releaseDecoder(handle: Long)

    // ---- Encoding ----
    override external fun beginEncoding(
        handle: Long,
        fileBytes: ByteArray,
        metadataHeader: ByteArray,
    ): Int

    override external fun nextEncodedTile(handle: Long): EncodedTileBytes?
    override external fun isEncodingComplete(handle: Long): Boolean

    // ---- Decoding ----
    override external fun ingestPacket(handle: Long, packetBytes: ByteArray): Boolean
    override external fun takeReconstructedFile(handle: Long): ByteArray?
    override external fun packetsCollected(handle: Long): Int
    override external fun packetsRequired(handle: Long): Int

    // ---- Inner code ----
    override external fun encodeSymbols(handle: Long, packetBytes: ByteArray): IntArray
    override external fun decodeSymbols(
        handle: Long,
        softSymbols: SoftSymbolProbabilities,
    ): ByteArray

    // ---- RaptorQ ----
    override external fun raptorQParams(payloadBytes: Long, symbolBytes: Int): RaptorQParams

    // ---- Adaptation ----
    /**
     * Not declared on the [CodecBridge] interface to keep the public
     * contract minimal; [PrismCodecImpl] calls it reflectively via the
     * concrete type. Native side reconfigures the active policy.
     */
    external fun reconfigurePolicy(handle: Long, distributionId: Int, targetOverhead: Float)

    // ---- Diagnostics ----
    override external fun lastErrorCode(handle: Long): Int
    override external fun lastErrorMessage(handle: Long): String
}
