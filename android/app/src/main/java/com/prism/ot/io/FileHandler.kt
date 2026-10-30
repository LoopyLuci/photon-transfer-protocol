package com.prism.ot.io

import android.net.Uri
import com.prism.ot.core.PrismResult
import kotlinx.coroutines.flow.Flow

/**
 * SAF-based file reader/writer for arbitrary binary payloads.
 *
 * Design contract:
 *  - Never loads the whole file into memory. All operations stream.
 *  - Accepts `content://` URIs only. File-path URIs are rejected; the
 *    caller must resolve them through SAF or copy into app-private storage
 *    first.
 *  - Every method is safe to cancel; partial writes are removed on
 *    cancellation by the implementation.
 *
 * The encoder side uses [openSource] to read the file the user picked.
 * The decoder side uses [openSink] to stream reconstructed bytes directly
 * to a user-chosen destination (or to app-private storage via
 * [ReceivedFileStore]).
 */
interface FileHandler {

    /**
     * Opens a streaming source for a user-selected `content://` URI.
     * The returned [FileSource] is single-use; callers must close it.
     */
    suspend fun openSource(uri: Uri): PrismResult<FileSource>

    /**
     * Opens a streaming sink for a destination `content://` URI created
     * via `ACTION_CREATE_DOCUMENT`. The sink truncates any existing
     * content and writes from offset 0.
     */
    suspend fun openSink(uri: Uri): PrismResult<FileSink>

    /**
     * Resolves display metadata (name, size, MIME type) without opening
     * a stream. Used to populate [FileMetadata] before transfer starts.
     */
    suspend fun stat(uri: Uri): PrismResult<SourceFileInfo>

    /**
     * Persists a read/write grant for the URI across process death.
     * Must be called immediately after the SAF picker returns if the
     * transfer will outlive the activity.
     */
    suspend fun persistPermission(uri: Uri, write: Boolean): PrismResult<Unit>
}

/**
 * Streaming source. [read] fills [buffer] and returns the number of bytes
 * read, or -1 at end of stream. Implementations may be called from any
 * dispatcher but are not themselves thread-safe.
 */
interface FileSource : AutoCloseable {
    val uri: Uri
    val declaredSizeBytes: Long
    suspend fun read(buffer: ByteArray): Int
}

/**
 * Streaming sink. [write] appends [length] bytes from [buffer]. Returns
 * only after the bytes are accepted by the underlying stream.
 */
interface FileSink : AutoCloseable {
    val uri: Uri
    suspend fun write(buffer: ByteArray, offset: Int, length: Int)
    suspend fun flush()
}

data class SourceFileInfo(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val lastModifiedEpochMs: Long,
)
