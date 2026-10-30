package com.prism.ot.ml

import android.content.Context
import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.ml.model.ModelStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Responsible for:
 *  1. Verifying that a model asset exists and is non-empty.
 *  2. Copying the asset to a memory-mappable cache file when required by
 *     LiteRT's [CompiledModel] (it cannot read directly from assets on all
 *     API levels without a file descriptor).
 *  3. Caching status so repeated loads are cheap.
 *  4. Exposing a ZOI asset handle for the native Rust path.
 *
 * This class deliberately does not create interpreters. Model wrappers own
 * their own interpreter lifecycle so that GPU delegate warm-up and ZOI
 * memory mapping can be tuned per model.
 */
@Singleton
class ModelLoader @Inject constructor(
    private val context: Context,
    private val logger: PrismLogger,
) {

    private val statusCache = ConcurrentHashMap<MlModelId, ModelStatus>()
    private val assetMutex = Mutex()
    private val cachedFiles = ConcurrentHashMap<MlModelId, File>()

    fun listMissingModels(): List<String> {
        val missing = mutableListOf<String>()
        MlModelId.entries.forEach { id ->
            if (!assetExists(id.assetName)) missing += id.assetName
        }
        return missing
    }

    fun assetExists(assetName: String): Boolean = runCatching {
        context.assets.open("models/$assetName").use { it.available() > 0 }
    }.getOrDefault(false)

    fun status(id: MlModelId): ModelStatus =
        statusCache[id] ?: ModelStatus.NotLoaded

    fun recordStatus(id: MlModelId, status: ModelStatus) {
        statusCache[id] = status
        when (status) {
            is ModelStatus.Loaded ->
                logger.i(TAG, "${id.displayName} loaded on ${status.accelerator.displayName} " +
                    "in ${status.loadTimeMillis} ms")
            is ModelStatus.Failed ->
                logger.e(TAG, "${id.displayName} failed: ${status.reason}")
            ModelStatus.NotLoaded -> Unit
        }
    }

    /**
     * Returns a stable File path for a LiteRT model. Copies from assets on
     * first use. The returned file is suitable for
     * `CompiledModel.create(file, options)` or `Interpreter(file, options)`.
     */
    suspend fun materialize(id: MlModelId): File = assetMutex.withLock {
        cachedFiles[id]?.let { if (it.exists() && it.length() > 0) return it }
        val out = File(context.cacheDir, "prism_models/${id.assetName}")
        out.parentFile?.mkdirs()
        context.assets.open("models/${id.assetName}").use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        require(out.length() > 0) { "Materialised model ${id.assetName} is empty" }
        cachedFiles[id] = out
        out
    }

    /**
     * Returns the raw bytes of a zero-overhead model. ZOI models are small
     * enough (typically < 2 MB) that holding them in native memory is
     * cheap; the Rust side maps them once and reuses across calls.
     */
    suspend fun readBytes(id: MlModelId): ByteArray = assetMutex.withLock {
        context.assets.open("models/${id.assetName}").use { it.readBytes() }
    }

    /**
     * Preferred accelerator ladder. NNAPI is intentionally absent:
     * it was deprecated in Android 15 and LiteRT's GPU delegate plus
     * XNNPACK CPU fallback is the supported path.
     */
    fun acceleratorLadder(id: MlModelId): List<Accelerator> {
        if (id.isZeroOverhead) {
            return listOf(Accelerator.ZERO_OVERHEAD_NATIVE, Accelerator.CPU_REFERENCE)
        }
        return when (id) {
            MlModelId.M03_SUPER_RESOLUTION,
            MlModelId.M09_DEBLURRER,
            MlModelId.M13_CHANNEL_GENERATOR -> listOf(
                Accelerator.GPU,
                Accelerator.CPU_XNNPACK,
            )
            MlModelId.M02_LOCALISER,
            MlModelId.M08_FACE_TRACKER -> listOf(
                Accelerator.GPU,
                Accelerator.CPU_XNNPACK,
            )
            else -> listOf(
                Accelerator.CPU_XNNPACK,
                Accelerator.GPU,
            )
        }
    }

    companion object {
        private const val TAG = "ModelLoader"
    }
}
