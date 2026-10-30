package com.prism.ot.data.model

/**
 * Result of decoding a single spatial tile (static) or face (kinetic).
 * `packetBytes` is the fountain-encoded payload extracted from the tile.
 */
data class DecodedTile(
    val tileId: Int,
    val faceId: Int? = null,
    val packetBytes: ByteArray,
    /** Average confidence of the symbol classifier for this tile [0,1]. */
    val symbolConfidence: Float,
    /** True if the inner NB-LDPC corrected at least one symbol error. */
    val wasErrorCorrected: Boolean,
    /** Source frame timestamp for temporal ordering. */
    val capturedAtEpochMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedTile) return false
        return tileId == other.tileId &&
            faceId == other.faceId &&
            symbolConfidence == other.symbolConfidence &&
            wasErrorCorrected == other.wasErrorCorrected &&
            capturedAtEpochMs == other.capturedAtEpochMs &&
            packetBytes.contentEquals(other.packetBytes)
    }

    override fun hashCode(): Int {
        var result = tileId
        result = 31 * result + (faceId ?: 0)
        result = 31 * result + packetBytes.contentHashCode()
        result = 31 * result + symbolConfidence.hashCode()
        result = 31 * result + wasErrorCorrected.hashCode()
        result = 31 * result + capturedAtEpochMs.hashCode()
        return result
    }
}
