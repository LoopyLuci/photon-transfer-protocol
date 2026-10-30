package com.prism.ot.io

import android.net.Uri
import com.prism.ot.core.PrismResult

/**
 * Streaming SHA-256 verifier.
 *
 * Two callers matter:
 *  1. **Sender**: compute the hash of the source file *before* the fountain
 *     encoder starts, so it can be embedded in the metadata header.
 *  2. **Receiver**: compute the hash of the reconstructed bytes *while
 *     writing them to the sink*, so verification costs no extra pass.
 *
 * Implementations must use [okio.HashingSink] / [okio.HashingSource] rather
 * than a post-hoc `MessageDigest` pass over the finished file; on a 4 GB
 * APK the second pass alone can take minutes.
 */
interface Sha256Verifier {

    /** Hash an entire `content://` source by streaming it. */
    suspend fun hashSource(source: FileSource): PrismResult<String>

    /** Hash an app-private `File` by streaming it. */
    suspend fun hashFile(file: java.io.File): PrismResult<String>

    /**
     * Wrap a [FileSink] so that bytes written through it are hashed
     * incrementally. The returned [HashingFileSink] exposes the digest
     * once the sink is closed.
     */
    fun hashingSink(delegate: FileSink): HashingFileSink

    /**
     * Constant-time comparison of two lowercase hex digests.
     * Avoids leaking digest bytes through timing, which matters when a
     * malicious sender controls the transmitted hash.
     */
    fun constantTimeEquals(expectedHex: String, actualHex: String): Boolean
}

/**
 * A [FileSink] wrapper that computes SHA-256 of everything written.
 * [digestHex] is valid only after [close].
 */
interface HashingFileSink : FileSink {
    fun digestHex(): String
}
