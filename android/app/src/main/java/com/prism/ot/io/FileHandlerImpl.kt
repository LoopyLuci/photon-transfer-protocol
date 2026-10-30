package com.prism.ot.io

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF-based implementation of [FileHandler].
 *
 * Okio is used for buffering so that every read/write is a single system
 * call on an 8 KiB-aligned buffer. The SAF stream is wrapped in an Okio
 * Source/Sink; this gives us the same streaming semantics on
 * `content://` URIs that we would get on a raw `File`, without ever
 * touching a filesystem path.
 */
@Singleton
class FileHandlerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: PrismLogger,
) : FileHandler {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun openSource(uri: Uri): PrismResult<FileSource> =
        withContext(ioDispatcher) {
            runCatching {
                require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    "FileHandler only accepts content:// URIs, got $uri"
                }
                val info = statBlocking(uri)
                val input: InputStream = resolver.openInputStream(uri)
                    ?: error("openInputStream returned null for $uri")
                SafFileSource(uri, info.sizeBytes, input)
            }.fold(
                onSuccess = { PrismResult.Success(it) },
                onFailure = {
                    logger.e(TAG, "openSource failed for $uri", it)
                    PrismResult.Failure(PrismError.FileReadFailed(it.message ?: "openInputStream"), it)
                },
            )
        }

    override suspend fun openSink(uri: Uri): PrismResult<FileSink> =
        withContext(ioDispatcher) {
            runCatching {
                require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    "FileHandler only accepts content:// URIs, got $uri"
                }
                val output: OutputStream = resolver.openOutputStream(uri, "wt")
                    ?: error("openOutputStream returned null for $uri")
                SafFileSink(uri, output)
            }.fold(
                onSuccess = { PrismResult.Success(it) },
                onFailure = {
                    logger.e(TAG, "openSink failed for $uri", it)
                    PrismResult.Failure(PrismError.FileWriteFailed(it.message ?: "openOutputStream"), it)
                },
            )
        }

    override suspend fun stat(uri: Uri): PrismResult<SourceFileInfo> =
        withContext(ioDispatcher) {
            runCatching { statBlocking(uri) }.fold(
                onSuccess = { PrismResult.Success(it) },
                onFailure = {
                    logger.e(TAG, "stat failed for $uri", it)
                    PrismResult.Failure(PrismError.FileReadFailed(it.message ?: "stat"), it)
                },
            )
        }

    override suspend fun persistPermission(uri: Uri, write: Boolean): PrismResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                resolver.takePersistableUriPermission(uri, flags)
            }.fold(
                onSuccess = { PrismResult.Success(Unit) },
                onFailure = {
                    logger.w(TAG, "persistPermission failed for $uri", it)
                    PrismResult.Failure(PrismError.FileReadFailed(it.message ?: "persist"), it)
                },
            )
        }

    private fun statBlocking(uri: Uri): SourceFileInfo {
        var displayName = uri.lastPathSegment ?: "payload.bin"
        var sizeBytes = -1L
        var lastModified = 0L
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, android.provider.MediaStore.MediaColumns.LAST_MODIFIED),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val modIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.LAST_MODIFIED)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) displayName = cursor.getString(nameIdx)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                if (modIdx >= 0 && !cursor.isNull(modIdx)) lastModified = cursor.getLong(modIdx)
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        require(sizeBytes >= 0L) { "Cannot determine size of $uri" }
        return SourceFileInfo(displayName, sizeBytes, mime, lastModified)
    }

    private class SafFileSource(
        override val uri: Uri,
        override val declaredSizeBytes: Long,
        private val input: InputStream,
    ) : FileSource {
        private val buffered = input.source().buffer()
        override suspend fun read(buffer: ByteArray): Int = withContext(kotlinx.coroutines.Dispatchers.IO) {
            buffered.read(buffer)
        }
        override fun close() = runCatching { buffered.close() }.let { }
    }

    private class SafFileSink(
        override val uri: Uri,
        private val output: OutputStream,
    ) : FileSink {
        private val buffered = output.sink().buffer()
        override suspend fun write(buffer: ByteArray, offset: Int, length: Int) =
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                buffered.write(buffer, offset, length)
            }
        override suspend fun flush() = withContext(kotlinx.coroutines.Dispatchers.IO) {
            buffered.flush()
        }
        override fun close() = runCatching { buffered.close() }.let { }
    }

    companion object {
        private const val TAG = "FileHandler"
        const val MIME_ALL = "*/*"
    }
}
