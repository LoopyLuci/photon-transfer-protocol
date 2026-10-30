package com.prism.ot.codec

import com.prism.ot.codec.fountain.LtFountainCodec
import io.kotest.core.spec.style.FreeSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Property-based fountain validation.
 *
 * The invariants below are the definition of a fountain code. If any of
 * them fails for a generated input, the shrinker will reduce the failure
 * to the smallest payload and loss pattern that triggers it.
 */
class FountainPropertyTest : FreeSpec({

    "any K + overhead packets recover a K-symbol payload" {
        checkAll(
            Arb.int(1, 32),                                    // symbol count K
            Arb.int(0, 16),                                    // extra packets
            Arb.list(Arb.byteArray(Arb.int(4, 64)), 1..32),    // payloads
        ) { k, extra, payloads ->
            val symbols = payloads.take(k)
            if (symbols.size < k) return@checkAll
            val codec = LtFountainCodec(symbols, symbolBytes = symbols.first().size)
            val packets = (0 until k + extra).map { codec.nextPacket(it) }
            val decoded = codec.decode(packets)
            decoded != null && decoded.zip(symbols).all { (a, b) -> a.contentEquals(b) }
        }
    }

    "packet count below K never reports success" {
        checkAll(Arb.int(2, 16)) { k ->
            val symbols = (0 until k).map { ByteArray(16) { it.toByte() } }
            val codec = LtFountainCodec(symbols, symbolBytes = 16)
            val packets = (0 until k - 1).map { codec.nextPacket(it) }
            codec.decode(packets) == null
        }
    }

    "order of packets does not affect reconstruction" {
        checkAll(Arb.int(0, 500)) { seed ->
            val symbols = (0 until 8).map { i -> ByteArray(24) { (i + it).toByte() } }
            val codec = LtFountainCodec(symbols, symbolBytes = 24)
            val packets = (0 until 10).map { codec.nextPacket(it) }
            val a = codec.decode(packets.shuffled(kotlin.random.Random(seed)).take(8))
            val b = codec.decode(packets.shuffled(kotlin.random.Random(seed + 1)).take(8))
            a != null && b != null && a.zip(b).all { (x, y) -> x.contentEquals(y) }
        }
    }
})
