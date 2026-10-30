package com.prism.ot.codec.fountain

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import kotlin.random.Random

/**
 * Luby-transform invariants.
 *
 * These tests exercise the pure-Kotlin fallback that is also the reference
 * for the native RaptorQ path. The property tests are the important ones:
 * they verify that *any* sufficient set of packets reconstructs the source,
 * which is the defining property of a fountain code.
 */
class LtFountainCodecTest : FreeSpec({

    "round-trips a 4-symbol payload from exactly K packets" {
        val source = listOf(
            ByteArray(16) { 0x01 },
            ByteArray(16) { 0x02 },
            ByteArray(16) { 0x03 },
            ByteArray(16) { 0x04 },
        )
        val codec = LtFountainCodec(source, symbolBytes = 16)
        val packets = (0 until source.size).map { codec.nextPacket(it) }
        val decoded = codec.decode(packets)
        decoded shouldNotBe null
        decoded shouldHaveSize source.size
        decoded!!.zip(source).forEach { (actual, expected) ->
            actual.contentEquals(expected) shouldBe true
        }
    }

    "reconstructs from any K of K + 4 packets in random order" {
        forAll(Arb.int(0, 1_000)) { seed ->
            val rng = Random(seed)
            val source = (0 until 8).map { i -> ByteArray(32) { (i * 7 + it).toByte() } }
            val codec = LtFountainCodec(source, symbolBytes = 32)
            val allPackets = (0 until 12).map { codec.nextPacket(it) }
            val subset = allPackets.shuffled(rng).take(8)
            val decoded = codec.decode(subset)
            decoded != null && decoded.size == source.size &&
                decoded.zip(source).all { (a, b) -> a.contentEquals(b) }
        }
    }

    "returns null when fewer than K independent packets are supplied" {
        val source = (0 until 6).map { ByteArray(8) { it.toByte() } }
        val codec = LtFountainCodec(source, symbolBytes = 8)
        val packets = (0 until 3).map { codec.nextPacket(it) }
        codec.decode(packets) shouldBe null
    }

    "packet header encodes sequence, seed, and degree" {
        val source = listOf(ByteArray(8) { 0x42 })
        val codec = LtFountainCodec(source, symbolBytes = 8)
        val packet = codec.nextPacket(sequence = 99)
        val buf = java.nio.ByteBuffer.wrap(packet)
        buf.int shouldBe 99
        buf.long // seed, non-deterministic
        buf.int // degree
        buf.int shouldBe 8 // payload length
    }

    "handles a single-symbol payload (K = 1) without division by zero" {
        val source = listOf(ByteArray(12) { 0x7F })
        val codec = LtFountainCodec(source, symbolBytes = 12)
        val decoded = codec.decode(listOf(codec.nextPacket(0)))
        decoded shouldNotBe null
        decoded!!.single().contentEquals(source.single()) shouldBe true
    }
})
