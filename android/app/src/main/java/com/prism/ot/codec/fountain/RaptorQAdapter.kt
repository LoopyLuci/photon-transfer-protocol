package com.prism.ot.codec.fountain

import com.prism.ot.codec.CodecBridge
import com.prism.ot.codec.RaptorQParams
import com.prism.ot.codec.SessionConfig
import com.prism.ot.core.PrismConstants
import com.prism.ot.core.PrismLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RaptorQ adapter. RaptorQ (RFC 6330) is the production outer code because
 * it achieves near-optimal overhead — typically K + 2 packets recover K
 * source symbols — and has linear-time encoding/decoding.
 *
 * The actual arithmetic lives in the native Rust core (the
 * `fountain_raptor_q` crate provides an RFC-aligned implementation built on
 * `fountain_engine`). This adapter owns the session-level bookkeeping:
 * source-block partitioning, ESI allocation, and overhead policy.
 *
 * References:
 *  - RFC 6330: RaptorQ Forward Error Correction Scheme for Object Delivery.
 *  - `fountain_raptor_q` (Rust): systematic RaptorQ with RFC degree sets,
 *    LDPC relationships, and octet-field HDPC arithmetic.
 */
@Singleton
class RaptorQAdapter @Inject constructor(
    private val bridge: CodecBridge,
    private val logger: PrismLogger,
) {

    /**
     * Compute the source-block structure for a payload. RaptorQ operates on
     * a source block of K' symbols; larger payloads are split into multiple
     * blocks, each independently encoded.
     */
    fun planSourceBlocks(
        payloadBytes: Long,
        config: SessionConfig,
    ): List<RaptorQParams> {
        val symbolBytes = config.symbolBytes.coerceAtLeast(1)
        val totalSymbols = ((payloadBytes + symbolBytes - 1) / symbolBytes).toInt()
        if (totalSymbols <= 0) return emptyList()

        // RFC 6330 limits K' to 56,403 per source block. Use the standard
        // maximum and let the native core compute exact K'.
        val maxK = 56_403
        val blocks = mutableListOf<RaptorQParams>()
        var remaining = totalSymbols
        while (remaining > 0) {
            val k = minOf(remaining, maxK)
            val params = bridge.raptorQParams(k.toLong() * symbolBytes, symbolBytes)
            blocks += params
            remaining -= k
        }
        return blocks
    }

    /**
     * Returns the total number of repair ESIs needed to hit [overhead].
     * Repair ESIs start at `params.numTotal` (K' + precode symbols).
     */
    fun repairEsiCount(params: RaptorQParams, overhead: Float): Int {
        val sourceEsis = params.k
        val target = (sourceEsis * overhead).toInt()
        return (target - sourceEsis).coerceAtLeast(0)
    }

    /**
     * Validates that a received ESI is in range. ESIs in
     * `[params.k, params.numTotal)` are precode symbols and must not be
     * transmitted as repair symbols; they are reconstructed by the decoder.
     */
    fun isValidRepairEsi(params: RaptorQParams, esi: Int): Boolean =
        esi >= params.repairEsiStart && esi < params.numTotal + MAX_REPAIR_ESI

    companion object {
        /** RFC 6330 allows ESIs up to 2^24 - 1. */
        const val MAX_REPAIR_ESI: Int = 1 shl 24
    }
}
