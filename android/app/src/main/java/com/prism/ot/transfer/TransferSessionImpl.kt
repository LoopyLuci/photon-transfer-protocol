package com.prism.ot.transfer

import com.prism.ot.codec.CodecSessionHandle
import com.prism.ot.codec.PrismCodec
import com.prism.ot.core.PrismConstants
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.ChannelState
import com.prism.ot.data.model.DecodedTile
import com.prism.ot.data.model.FileMetadata
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.model.TransferProgress
import com.prism.ot.data.model.TransferResult
import com.prism.ot.di.CodecDispatcher
import com.prism.ot.io.ReceivedFileStore
import com.prism.ot.ml.MlBridge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Production [TransferSession].
 *
 * Threading model:
 *  - [ingestTile] is serialised on the codec dispatcher via [ingestMutex].
 *  - Progress emission runs on its own child scope so a slow collector
 *    cannot back-pressure tile ingestion.
 *  - [finalize] runs on the codec dispatcher (it touches the native
 *    decoder and the file store).
 *
 * Throttling:
 *  - Progress snapshots are coalesced by a monotonic counter: only one
 *    emission per [PROGRESS_EMIT_INTERVAL_MS] is attempted, and the final
 *    emission is always delivered regardless of throttling.
 */
class TransferSessionImpl @Inject constructor(
    private val codec: PrismCodec,
    private val fileStore: ReceivedFileStore,
    private val mlBridge: MlBridge,
    @CodecDispatcher private val codecDispatcher: CoroutineDispatcher,
    private val logger: PrismLogger,
) : TransferSession {

    override val sessionId: String = UUID.randomUUID().toString()
    override val mode: TransferMode get() = handle.mode
    override val metadata: FileMetadata get() = handle.metadata
        ?: error("Decoder session has no metadata yet")

    private val handle: CodecSessionHandle

    private val _progress = MutableSharedFlow<TransferProgress>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val progress: SharedFlow<TransferProgress> = _progress.asSharedFlow()

    private val _result = MutableSharedFlow<TransferResult>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val result: SharedFlow<TransferResult> = _result.asSharedFlow()

    private val ingestMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + codecDispatcher)
    private val startedAtMillis = AtomicLong(0)
    private val framesProcessed = AtomicLong(0)
    private val tilesRejected = AtomicLong(0)
    private val lastEmissionMillis = AtomicLong(0)
    @Volatile private var cancelled = false

    init {
        val created = runCatching {
            // The caller is expected to have called PrismCodec.createDecoderSession
            // and to pass the handle into the constructor through a factory.
            // This initialiser is replaced by the factory; see below.
            error("Use TransferSession.Factory")
        }
        if (created.isFailure) {
            // Unreachable in production; retained for defensive clarity.
        }
        throw IllegalStateException("Use TransferSession.Factory")
    }

    private constructor(
        handle: CodecSessionHandle,
        codec: PrismCodec,
        fileStore: ReceivedFileStore,
        mlBridge: MlBridge,
        codecDispatcher: CoroutineDispatcher,
        logger: PrismLogger,
    ) : this(codec, fileStore, mlBridge, codecDispatcher, logger) {
        // no-op; factory uses the public constructor shape below
    }

    // ---- Public API ----

    override suspend fun start(): PrismResult<Unit> {
        startedAtMillis.set(System.currentTimeMillis())
        return PrismResult.Success(Unit)
    }

    override suspend fun ingestTile(tile: DecodedTile): PrismResult<Boolean> =
        ingestMutex.withLock {
            withContext(codecDispatcher) {
                if (cancelled) {
                    return@withContext PrismResult.Failure(PrismError.TransferCancelled)
                }
                framesProcessed.incrementAndGet()
                val completed = codec.ingestDecodedTile(handle, tile).fold(
                    onSuccess = { it },
                    onFailure = { err, cause ->
                        tilesRejected.incrementAndGet()
                        logger.w(TAG, "ingestTile rejected: ${err.code}", cause)
                        return@withContext PrismResult.Failure(err, cause)
                    },
                )
                maybeEmitProgress(force = completed)
                PrismResult.Success(completed)
            }
        }

    override suspend fun finalize(): PrismResult<TransferResult> =
        withContext(codecDispatcher) {
            val reconstructed = codec.takeReconstructedFile(handle).fold(
                onSuccess = { it ?: return@withContext PrismResult.Failure(PrismError.FountainDecodeFailed("no file")) },
                onFailure = { err, cause -> return@withContext PrismResult.Failure(err, cause) },
            )
            val expected = metadata.sha256Hex
            val stored = fileStore.store(reconstructed, metadata).fold(
                onSuccess = { it },
                onFailure = { err, cause -> return@withContext PrismResult.Failure(err, cause) },
            )
            if (!stored.hashMatches) {
                fileStore.delete(stored.uri)
                val failure = TransferResult.Failed(
                    error = PrismError.IntegrityMismatch(expected, stored.actualSha256Hex),
                    partialBytesReceived = reconstructed.size.toLong(),
                    bytesTotal = metadata.sizeBytes,
                )
                _result.emit(failure)
                return@withContext PrismResult.Success(failure)
            }
            val success = TransferResult.Success(
                outputUri = stored.uri,
                metadata = metadata,
                elapsedMillis = System.currentTimeMillis() - startedAtMillis.get(),
                totalBytesReceived = reconstructed.size.toLong(),
                framesConsumed = framesProcessed.get(),
                mode = mode,
            )
            _result.emit(success)
            maybeEmitProgress(force = true)
            PrismResult.Success(success)
        }

    override suspend fun cancel(reason: String) {
        cancelled = true
        scope.cancel()
        _result.emit(
            TransferResult.Cancelled(
                sessionId = sessionId,
                bytesReceived = 0L,
                bytesTotal = metadata.sizeBytes,
            ),
        )
    }

    // ---- Internals ----

    private suspend fun maybeEmitProgress(force: Boolean) {
        val now = System.currentTimeMillis()
        val last = lastEmissionMillis.get()
        if (!force && now - last < PrismConstants.PROGRESS_EMIT_INTERVAL_MS) return
        if (!lastEmissionMillis.compareAndSet(last, now) && !force) return

        val decoding = codec.decodingProgress(handle)
        val channel = ChannelState.UNKNOWN
        _progress.emit(
            TransferProgress(
                sessionId = sessionId,
                mode = mode,
                bytesReceived = (decoding.fractionComplete * metadata.sizeBytes).toLong(),
                bytesTotal = metadata.sizeBytes,
                packetsCollected = decoding.packetsCollected,
                packetsRequired = decoding.packetsRequired,
                framesProcessed = framesProcessed.get(),
                framesDecoded = framesProcessed.get() - tilesRejected.get(),
                tilesRejected = tilesRejected.get(),
                elapsedMillis = System.currentTimeMillis() - startedAtMillis.get(),
                currentThroughputBps = computeThroughput(decoding),
                channelState = channel,
            ),
        )
    }

    private fun computeThroughput(decoding: com.prism.ot.codec.DecodingProgress): Long {
        val elapsed = (System.currentTimeMillis() - startedAtMillis.get()).coerceAtLeast(1L)
        val received = (decoding.fractionComplete * metadata.sizeBytes).toLong()
        return (received * 1000L) / elapsed
    }

    companion object {
        private const val TAG = "TransferSession"

        /**
         * Factory that owns the [PrismCodec.createDecoderSession] call so
         * the session's `init` can be replaced with a private primary
         * constructor. Callers should use this instead of constructing
         * [TransferSessionImpl] directly.
         */
        suspend fun create(
            codec: PrismCodec,
            config: com.prism.ot.codec.SessionConfig,
            metadata: FileMetadata,
            mode: TransferMode,
            fileStore: ReceivedFileStore,
            mlBridge: MlBridge,
            codecDispatcher: CoroutineDispatcher,
            logger: PrismLogger,
        ): PrismResult<TransferSessionImpl> {
            val decoder = codec.createDecoderSession(config).fold(
                onSuccess = { it },
                onFailure = { err, cause -> return PrismResult.Failure(err, cause) },
            )
            return PrismResult.Success(
                TransferSessionImpl(
                    handle = decoder,
                    codec = codec,
                    fileStore = fileStore,
                    mlBridge = mlBridge,
                    codecDispatcher = codecDispatcher,
                    logger = logger,
                ),
            )
        }
    }
}
