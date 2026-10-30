package com.prism.ot.data.model

/**
 * A single fountain-encoded packet extracted from a decoded tile.
 * [sequence] is the encoded-symbol index (0-based). [seed] drives
 * the deterministic LT/RaptorQ neighbour selection used by the decoder.
 */
data class FountainPacket(
    val segmentIndex: Int,
    val sequence: Int,
    val seed: Long,
    val degree: Int,
    val payload: ByteArray,
) {
    val size: Int get() = payload.size + HEADER_BYTES

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FountainPacket) return false
        return segmentIndex == other.segmentIndex &&
            sequence == other.sequence &&
            seed == other.seed &&
            degree == other.degree &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = segmentIndex
        result = 31 * result + sequence
        result = 31 * result + seed.hashCode()
        result = 31 * result + degree
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_BYTES: Int = 24
    }
}
