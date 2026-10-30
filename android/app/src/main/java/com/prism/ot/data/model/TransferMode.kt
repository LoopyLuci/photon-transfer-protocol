package com.prism.ot.data.model

import kotlinx.serialization.Serializable

/**
 * Which physical transport is being used for the current transfer.
 * [FALLBACK] is used when the app detects that the custom PRISM symbol
 * cannot be reliably decoded on the current device and silently falls back
 * to standard QR framing with fountain coding.
 */
@Serializable
enum class TransferMode {
    STATIC,
    KINETIC,
    FALLBACK;

    val displayName: String
        get() = when (this) {
            STATIC -> "Static"
            KINETIC -> "Kinetic"
            FALLBACK -> "Fallback (QR)"
        }
}
