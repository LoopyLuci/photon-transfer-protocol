package com.prism.ot.ml.wrapper

import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.ModelLoader
import com.prism.ot.ml.model.Accelerator
import com.prism.ot.ml.model.MlModelId
import com.prism.ot.ml.model.ModelStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared lifecycle for LiteRT-backed wrappers.
 *
 * Subclasses provide [buildInterpreter], which receives an [Accelerator]
 * and returns an opaque handle (the concrete type is hidden behind
 * [InterpreterHandle] so wrappers can mix LiteRT and ZOI without
 * leaking types into this base class).
 */
abstract class BaseModelWrapper<T : Any>(
    protected val loader: ModelLoader,
    protected val logger: PrismLogger,
    val modelId: MlModelId,
) {

    protected interface InterpreterHandle : AutoCloseable

    private val mutex = Mutex()
    @Volatile private var handle: InterpreterHandle? = null
    @Volatile private var activeAccelerator: Accelerator? = null

    suspend fun initialize() {
        mutex.withLock {
            if (handle != null) return
            val ladder = loader.acceleratorLadder(modelId)
            var lastError: Throwable? = null
            for (accel in ladder) {
                val started = System.currentTimeMillis()
                try {
                    val built = buildInterpreter(accel)
                    handle = built
                    activeAccelerator = accel
                    loader.recordStatus(
                        modelId,
                        ModelStatus.Loaded(accel, System.currentTimeMillis() - started),
                    )
                    return
                } catch (t: Throwable) {
                    lastError = t
                    logger.w(TAG, "${modelId.displayName} failed on ${accel.displayName}: ${t.message}", t)
                }
            }
            loader.recordStatus(
                modelId,
                ModelStatus.Failed(
                    reason = lastError?.message ?: "no accelerator succeeded",
                    recoverable = false,
                ),
            )
            throw IllegalStateException(
                "${modelId.displayName} could not be loaded on any accelerator",
                lastError,
            )
        }
    }

    fun status(): ModelStatus = loader.status(modelId)

    fun listMissingAssets(): List<String> =
        if (loader.assetExists(modelId.assetName)) emptyList() else listOf(modelId.assetName)

    protected fun requireHandle(): InterpreterHandle =
        handle ?: throw IllegalStateException("${modelId.displayName} not initialised")

    open suspend fun close() {
        mutex.withLock {
            runCatching { handle?.close() }
            handle = null
            activeAccelerator = null
        }
    }

    /**
     * Build an interpreter for [accelerator]. Throw if unsupported; the
     * base class will fall through to the next rung of the ladder.
     */
    protected abstract suspend fun buildInterpreter(accelerator: Accelerator): InterpreterHandle

    private companion object {
        const val TAG = "ModelWrapper"
    }
}
