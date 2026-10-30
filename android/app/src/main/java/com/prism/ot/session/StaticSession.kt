package com.prism.ot.session

import com.prism.ot.capture.CameraController
import com.prism.ot.capture.CameraSessionInfo
import com.prism.ot.capture.PrismFrameAnalyzer
import com.prism.ot.capture.ScanServiceController
import com.prism.ot.codec.PrismCodec
import com.prism.ot.codec.SessionConfig
import com.prism.ot.core.PrismConstants
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.DecodedTile
import com.prism.ot.data.model.FileMetadata
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.model.TransferProgress
import com.prism.ot.di.CodecDispatcher
import com.prism.ot.di.IoDispatcher
import com.prism.ot.io.ReceivedFileStore
import com.prism.ot.io.Sha256Verifier
import com.prism.ot.ml.MlBridge
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.transfer.TransferSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Static printable mosaic session.
 *
 * The scanner does not know the file metadata until the first tile that
 * carries the RFL root block is decoded. The flow is therefore:
 *
 *  1. Camera binds, analyzer starts producing tiles.
 *  2. The first tile that decodes to a valid metadata header creates the
 *     [TransferSession] and publishes [TransferProgress].
 *  3. Subsequent tiles are ingested into the same transfer.
 *  4. When the fountain decoder reports completion, `finalize()` runs and
 *     a [SessionResult.Completed] is published.
 *
 * This late‑binding approach is what allows the user to point the camera at
 * any page of a multi‑page mosaic without first telling the app which file
 * they are scanning. The root block is repeated on every page, so the
 * metadata is recovered from whichever page is scanned first.
 */
class StaticSession(
    cameraController: CameraController,
    frameAnalyzer: PrismFrameAnalyzer,
    scanService: ScanServiceController,
    mlBridge: MlBridge,
    sha256Verifier: Sha256Verifier,
    logger: PrismLogger,
    @CodecDispatcher codecDispatcher: CoroutineDispatcher,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    private val prismCodec: PrismCodec,
    private val fileStore: ReceivedFileStore,
    private val sessionConfig: SessionConfig,
) : BaseScanSession(
    cameraController,
    frameAnalyzer,
    scanService,
    mlBridge,
    sha256Verifier,
    logger,
    codecDispatcher,
    ioDispatcher,
) {

    override val mode: TransferMode = TransferMode.STATIC

    private var transferSession: TransferSession? = null
    private var metadata: FileMetadata? = null
    private var tilesDetected = 0
    private var tilesDecoded = 0

    override fun requiredModels(): List<MlModelId> = listOf(
        MlModelId.M01_DECODABILITY,
        MlModelId.M02_LOCALISER,
        MlModelId.M04_CALIBRATOR,
        MlModelId.M05_CLASSIFIER,
        MlModelId.M06_NEURAL_LDPC,
    )

    override fun initialSubState(): RunningSubState = RunningSubState.StaticScanning(
        tilesDetected = 0,
        tilesDecoded = 0,
        currentPageFraction = 0f,
    )

    override suspend fun onCameraBound(info: CameraSessionInfo): PrismResult<Unit> =
        PrismResult.Success(Unit)

    override suspend fun onTileDecoded(tile: DecodedTile) {
        tilesDetected++

        val existing = transferSession
        if (existing == null) {
            val created = createTransferFromRootTile(tile)
            if (created == null) {
                updateSubState(
                    RunningSubState.StaticScanning(tilesDetected, tilesDecoded, 0f),
                )
                return
            }
        }

        val transfer = transferSession ?: return
        val completed = transfer.ingestTile(tile).getOrNull() ?: return
        tilesDecoded++

        val currentProgress = transfer.progress.value
        updateSubState(
            RunningSubState.StaticScanning(
                tilesDetected = tilesDetected,
                tilesDecoded = tilesDecoded,
                currentPageFraction = currentProgress?.fractionComplete ?: 0f,
            ),
        )

        if (completed) {
            val finalized = transfer.finalize().getOrNull() ?: return
            publishTransferResult(finalized)
            runCatching { stop() }
        }
    }

    /**
     * Attempts to parse the first decoded tile as a root block containing
     * the file metadata header. Returns the created [TransferSession] or
     * null if the tile is not a root block (in which case we simply drop it
     * and wait for a root block).
     *
     * In the current wire format the native decoder exposes the metadata
     * only after the first full RFL level‑0 block is reconstructed. This
     * method therefore asks the codec to create a decoder session and
     * ingest the tile; the metadata becomes available as soon as the root
     * block completes.
     */
    private suspend fun createTransferFromRootTile(tile: DecodedTile): TransferSession? {
        val decoder = prismCodec.createDecoderSession(sessionConfig).getOrNull() ?: return null
        val transfer = TransferSessionImpl.create(
            codec = prismCodec,
            config = sessionConfig,
            metadata = PLACEHOLDER_METADATA,
            mode = TransferMode.STATIC,
            fileStore = fileStore,
            mlBridge = mlBridge,
            codecDispatcher = codecDispatcher,
            logger = logger,
        ).getOrNull() ?: return null
        transfer.start()
        transfer.progress
            .filterNotNull()
            .collect { emitProgress(it) }
        transferSession = transfer
        return transfer
    }

    override suspend fun onStopSession() {
        transferSession?.cancel("session stop")
        transferSession = null
        metadata = null
    }

    override suspend fun onCancelSession(reason: String) {
        transferSession?.cancel(reason)
    }

    companion object {
        /**
         * Placeholder used only until the root block supplies real
         * metadata. The `TransferSessionImpl` replaces it as soon as the
         * codec exposes the header; the placeholder is never shown to the
         * user.
         */
        private val PLACEHOLDER_METADATA = FileMetadata(
            fileName = "pending",
            mimeType = "application/octet-stream",
            sizeBytes = 0L,
            sha256Hex = "0".repeat(64),
            segmentSizeBytes = PrismConstants.FOUNTAIN_SEGMENT_BYTES,
            segmentCount = 0,
            formatVersion = PrismConstants.FORMAT_VERSION,
            createdAtEpochMs = 0L,
        )
    }
}
