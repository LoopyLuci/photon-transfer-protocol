package com.prism.ot.codec

import kotlinx.serialization.Serializable

/**
 * Immutable configuration handed to the native encoder/decoder at session
 * creation. Must be identical on both ends of the transfer (it is embedded
 * in the fountain root block).
 */
@Serializable
data class SessionConfig(
    val formatVersion: Int,
    val alphabetMode: AlphabetMode,
    val innerCodeMode: InnerCodeMode,
    val outerCodeMode: OuterCodeMode,
    val segmentBytes: Int,
    val symbolBytes: Int,
    val targetOverhead: Float,
    val distributionId: Int,
    val useHierarchicalPartitioning: Boolean,
    val errorCorrectionBudget: Float,   // 0.05–0.90, HAP-MLC outer EC fraction
)

enum class AlphabetMode {
    DENSE_64,          // 16 shapes × 4 colours = 6 bits/cell
    DENSE_PLUS_128,    // 16 shapes × 8 colours = 7 bits/cell
    READABLE_52,       // digits + letters + 16 shapes
}

enum class InnerCodeMode {
    NB_LDPC_GF64,
    NB_LDPC_GF128,
    REED_SOLOMON_FALLBACK,
    NONE,
}

enum class OuterCodeMode {
    RAPTORQ,           // RFC 6330
    LT_FOUNTAIN,       // Luby transform fallback
    REPETITION,        // testing only
}

/**
 * RaptorQ source-block parameters returned by the native core.
 * [k] is the extended source-symbol count K' from RFC 6330.
 * [numTotal] is K' + precode symbols; repair ESIs start at [numTotal].
 */
data class RaptorQParams(
    val k: Int,
    val numTotal: Int,
    val symbolBytes: Int,
    val sourceBlockCount: Int,
) {
    val repairEsiStart: Int get() = numTotal
}
