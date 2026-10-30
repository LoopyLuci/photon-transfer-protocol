package com.prism.ot.io

import android.net.Uri
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.FileMetadata

/**
 * Stores reconstructed files in app-private storage, verifies SHA-256
 * while writing, and returns a `FileProvider` content URI suitable for
 * handing to another app.
 *
 * Delivery contract:
 *  - The file is only exposed if its SHA-256 matches [FileMetadata.sha256Hex].
 *  - The returned URI is a `content://` URI from
 *    `FileProvider.getUriForFile()`; the caller must attach
 *    `FLAG_GRANT_READ_URI_PERMISSION` when passing it to an Intent.
 *  - The file lives under `filesDir/received/` and is cleaned up by
 *    [delete] on failure or by the user on success.
 */
interface ReceivedFileStore {

    suspend fun store(
        bytes: ByteArray,
        metadata: FileMetadata,
    ): PrismResult<StoredFile>

    suspend fun delete(uri: Uri)

    /**
     * Best-effort cleanup of orphaned partial files older than
     * [maxAgeMillis]. Called on app start.
     */
    suspend fun cleanupOrphans(maxAgeMillis: Long = 7L * 24 * 60 * 60 * 1000)

    data class StoredFile(
        val uri: Uri,
        val localPath: String,
        val sizeBytes: Long,
        val actualSha256Hex: String,
        val hashMatches: Boolean,
    )
}
