package com.prism.ot.io

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.FileMetadata
import com.prism.ot.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private implementation of [ReceivedFileStore].
 *
 * Why app-private + FileProvider, not SAF:
 *  - The receiver does not know where the user wants the file yet. SAF
 *    requires the destination URI up front. We therefore write to
 *    `filesDir/received/`, verify the hash, and only then hand out a
 *    content URI. The user can then "Save to…" via SAF if desired, or
 *    launch the APK installer directly.
 *  - This is the standard secure sharing pattern: send the receiving app a
 *    content URI and grant temporary read access; never expose a raw
 *    filesystem path.
 */
@Singleton
class ReceivedFileStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: PrismLogger,
) : ReceivedFileStore {

    private val receivedDir: File by lazy {
        File(context.filesDir, "received").apply { mkdirs() }
    }

    override suspend fun store(
        bytes: ByteArray,
        metadata: FileMetadata,
    ): PrismResult<ReceivedFileStore.StoredFile> = withContext(ioDispatcher) {
        runCatching {
            val safeName = sanitize(metadata.fileName)
            val target = File(receivedDir, "${System.currentTimeMillis()}_$safeName")
            val digest = MessageDigest.getInstance("SHA-256")

            target.sink().buffer().use { sink ->
                var offset = 0
                while (offset < bytes.size) {
                    val n = minOf(BUFFER_BYTES, bytes.size - offset)
                    digest.update(bytes, offset, n)
                    sink.write(bytes, offset, n)
                    offset += n
                }
                sink.flush()
            }

            val actual = digest.digest().toHex()
            val matches = actual.equals(metadata.sha256Hex, ignoreCase = true)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
            ReceivedFileStore.StoredFile(
                uri = uri,
                localPath = target.absolutePath,
                sizeBytes = target.length(),
                actualSha256Hex = actual,
                hashMatches = matches,
            )
        }.fold(
            onSuccess = { PrismResult.Success(it) },
            onFailure = {
                logger.e(TAG, "store failed for ${metadata.fileName}", it)
                PrismResult.Failure(PrismError.FileWriteFailed(it.message ?: "store"), it)
            },
        )
    }

    override suspend fun delete(uri: Uri) = withContext(ioDispatcher) {
        runCatching {
            val name = uri.lastPathSegment ?: return@withContext
            val file = File(receivedDir, name)
            if (file.exists()) file.delete()
        }.onFailure { logger.w(TAG, "delete failed for $uri", it) }
    }

    override suspend fun cleanupOrphans(maxAgeMillis: Long) = withContext(ioDispatcher) {
        runCatching {
            val cutoff = System.currentTimeMillis() - maxAgeMillis
            receivedDir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoff) f.delete()
            }
        }.onFailure { logger.w(TAG, "cleanupOrphans failed", it) }
    }

    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.isBlank()) "received.bin" else cleaned.take(120)
    }

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        val hex = "0123456789abcdef"
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            out[i * 2] = hex[v ushr 4]
            out[i * 2 + 1] = hex[v and 0x0F]
        }
        return String(out)
    }

    companion object {
        private const val TAG = "ReceivedFileStore"
        private const val BUFFER_BYTES = 64 * 1024
    }
}
