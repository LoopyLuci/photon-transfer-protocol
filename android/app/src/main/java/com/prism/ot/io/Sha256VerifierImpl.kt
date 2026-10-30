package com.prism.ot.io

import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.HashingSink
import okio.HashingSource
import okio.blackholeSink
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Okio-backed streaming SHA-256 verifier.
 *
 * `HashingSource` and `HashingSink` are used because they compute the
 * digest as bytes flow through, eliminating the separate verification
 * pass that would otherwise be required after reconstruction.
 */
@Singleton
class Sha256VerifierImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: PrismLogger,
) : Sha256Verifier {

    override suspend fun hashSource(source: FileSource): PrismResult<String> =
        withContext(ioDispatcher) {
            runCatching {
                val buffer = Buffer()
                val tmp = ByteArray(BUFFER_BYTES)
                val digest = MessageDigest.getInstance("SHA-256")
                while (true) {
                    val n = source.read(tmp)
                    if (n < 0) break
                    digest.update(tmp, 0, n)
                }
                digest.digest().toHex()
            }.fold(
                onSuccess = { PrismResult.Success(it) },
                onFailure = {
                    logger.e(TAG, "hashSource failed", it)
                    PrismResult.Failure(PrismError.FileReadFailed(it.message ?: "hash"), it)
                },
            )
        }

    override suspend fun hashFile(file: File): PrismResult<String> =
        withContext(ioDispatcher) {
            runCatching {
                val source = file.source().buffer()
                HashingSource.sha256(source).use { hashing ->
                    val sink = HashingSink.sha256(blackholeSink()).buffer()
                    hashing.use { src ->
                        while (true) {
                            val n = src.read(Buffer(), BUFFER_BYTES)
                            if (n == -1L) break
                        }
                    }
                    hashing.hash.hex()
                }
            }.fold(
                onSuccess = { PrismResult.Success(it) },
                onFailure = {
                    logger.e(TAG, "hashFile failed for ${file.name}", it)
                    PrismResult.Failure(PrismError.FileReadFailed(it.message ?: "hashFile"), it)
                },
            )
        }

    override fun hashingSink(delegate: FileSink): HashingFileSink =
        OkioHashingFileSink(delegate)

    override fun constantTimeEquals(expectedHex: String, actualHex: String): Boolean {
        if (expectedHex.length != actualHex.length) return false
        val a = expectedHex.lowercase().toByteArray(Charsets.US_ASCII)
        val b = actualHex.lowercase().toByteArray(Charsets.US_ASCII)
        return MessageDigest.isEqual(a, b)
    }

    private class OkioHashingFileSink(
        private val delegate: FileSink,
    ) : HashingFileSink {

        private val digest = MessageDigest.getInstance("SHA-256")
        private val shaSink = HashingSink.sha256(okio.blackholeSink()).buffer()
        @Volatile private var closed = false

        override val uri get() = delegate.uri

        override suspend fun write(buffer: ByteArray, offset: Int, length: Int) {
            digest.update(buffer, offset, length)
            shaSink.write(buffer, offset, length)
            delegate.write(buffer, offset, length)
        }

        override suspend fun flush() = delegate.flush()

        override fun digestHex(): String {
            check(closed) { "digestHex() called before close()" }
            return shaSink.hash.hex()
        }

        override fun close() {
            runCatching { shaSink.close() }
            runCatching { delegate.close() }
            closed = true
        }
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
        private const val TAG = "Sha256Verifier"
        private const val BUFFER_BYTES = 64 * 1024
    }
}
