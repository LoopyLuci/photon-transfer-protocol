package com.prism.ot.data.model

import kotlinx.serialization.Serializable

/**
 * Immutable description of a payload being sent or received.
 *
 * This header travels in the fountain code's root block (RFL level 0) so the
 * receiver can display progress, validate SHA-256, and reconstruct the file
 * even before any payload blocks are decoded.
 */
@Serializable
data class FileMetadata(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val segmentSizeBytes: Int,
    val segmentCount: Int,
    val formatVersion: Int,
    val createdAtEpochMs: Long,
    val appPackageName: String? = null,  // for APK payloads
    val appVersionName: String? = null,
) {
    init {
        require(sizeBytes >= 0) { "sizeBytes must be non-negative" }
        require(segmentCount >= 0) { "segmentCount must be non-negative" }
        require(sha256Hex.length == 64) { "sha256Hex must be 64 hex chars" }
    }

    val totalPacketsRequired: Int
        get() = ((segmentCount * segmentSizeBytes).toDouble() * 1.0)
            .toInt() + segmentCount  // 1 metadata packet per segment minimum
}
