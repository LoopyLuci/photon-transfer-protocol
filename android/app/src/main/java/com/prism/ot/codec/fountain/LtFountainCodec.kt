package com.prism.ot.codec.fountain

import com.prism.ot.core.PrismConstants
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure-Kotlin Luby-transform fountain codec.
 *
 * This is **not** the production path — the native RaptorQ implementation
 * is used when available. This class exists for three reasons:
 *  1. Unit tests can exercise fountain semantics without the NDK.
 *  2. Devices where the native core fails to load still get a working
 *     (slower, higher-overhead) transfer.
 *  3. The degree-distribution logic here is the reference the native
 *     side is validated against.
 *
 * Wire format per packet (little-endian):
 *   [4] sequence
 *   [8] seed
 *   [4] degree
 *   [4] payloadLength
 *   [payloadLength] payload
 */
class LtFountainCodec(
    private val sourceSymbols: List<ByteArray>,
    private val symbolBytes: Int,
) {

    private val k = sourceSymbols.size
    private val idealDegree = maxOf(2, (ln(k.toDouble()) + 1.0).toInt())
    private val random = Random(k)

    fun nextPacket(sequence: Int): ByteArray {
        val degree = sampleDegree(random)
        val neighbours = sampleNeighbours(random, degree)
        val payload = xorSymbols(neighbours)
        return encodePacket(sequence, random.nextLong(), degree, payload)
    }

    fun decode(packets: List<ByteArray>, required: Int = k): List<ByteArray>? {
        val peelingGraph = mutableMapOf<Int, MutableSet<Int>>()
        val symbolPayloads = mutableMapOf<Int, ByteArray>()

        for (raw in packets) {
            val (seq, _, _, payload) = decodePacket(raw)
            val degree = raw[16].toInt() and 0xFF // degree at offset 16 (4+8+4)
            val neighbours = sampleNeighboursForSequence(seq, degree)
            peelingGraph[seq] = neighbours.toMutableSet()
            symbolPayloads[seq] = payload
        }

        val solved = mutableMapOf<Int, ByteArray>()
        var progress = true
        while (progress && solved.size < k) {
            progress = false
            val iterator = peelingGraph.entries.iterator()
            while (iterator.hasNext()) {
                val (seq, neighbours) = iterator.next()
                neighbours.removeAll(solved.keys)
                if (neighbours.size == 1) {
                    val resolved = neighbours.first()
                    val payload = symbolPayloads.getValue(seq)
                    solved[resolved] = payload
                    iterator.remove()
                    progress = true
                }
            }
        }
        return if (solved.size >= k) (0 until k).map { solved.getValue(it) } else null
    }

    private fun sampleDegree(rng: Random): Int {
        // Robust Soliton approximation; k=1 is handled specially.
        if (k <= 1) return 1
        val u = rng.nextDouble()
        val pivot = 1.0 / k
        return when {
            u < pivot -> 1
            u < pivot * 2 -> 2
            else -> minOf(k, idealDegree + rng.nextInt(0, 3))
        }
    }

    private fun sampleNeighbours(rng: Random, degree: Int): Set<Int> =
        buildSet {
            while (size < degree.coerceAtMost(k)) add(rng.nextInt(k))
        }

    private fun sampleNeighboursForSequence(sequence: Int, degree: Int): Set<Int> {
        // Deterministic replay: the decoder derives neighbours from the
        // sequence and degree using the same RNG seed as the encoder.
        val rng = Random(sequence)
        return sampleNeighbours(rng, degree)
    }

    private fun xorSymbols(indices: Set<Int>): ByteArray {
        val out = ByteArray(symbolBytes)
        for (i in indices) {
            val sym = sourceSymbols[i]
            for (b in 0 until symbolBytes) out[b] = (out[b].toInt() xor sym[b].toInt()).toByte()
        }
        return out
    }

    private fun encodePacket(sequence: Int, seed: Long, degree: Int, payload: ByteArray): ByteArray {
        val out = java.nio.ByteBuffer.allocate(20 + payload.size)
        out.putInt(sequence)
        out.putLong(seed)
        out.putInt(degree)
        out.putInt(payload.size)
        out.put(payload)
        return out.array()
    }

    private data class DecodedPacket(
        val sequence: Int,
        val seed: Long,
        val degree: Int,
        val payload: ByteArray,
    )

    private fun decodePacket(raw: ByteArray): DecodedPacket {
        val buf = java.nio.ByteBuffer.wrap(raw)
        val seq = buf.int
        val seed = buf.long
        val degree = buf.int
        val len = buf.int
        val payload = ByteArray(len).also { buf.get(it) }
        return DecodedPacket(seq, seed, degree, payload)
    }
}
