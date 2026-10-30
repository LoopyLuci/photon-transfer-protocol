package com.prism.ot.transfer

import com.prism.ot.core.PrismConstants
import com.prism.ot.data.model.TransferProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Throttled progress emitter used by [TransferSessionImpl] and by the
 * static/kinetic session orchestrators in Batch 8.
 *
 * Behaviour:
 *  - Coalesces bursts: at most one emission per [intervalMs].
 *  - Never drops a [force]-d emission.
 *  - Uses a monotonic counter so concurrent producers do not race on the
 *    timestamp.
 */
class TransferProgressEmitter(
    private val intervalMs: Long = PrismConstants.PROGRESS_EMIT_INTERVAL_MS,
) {
    private val _flow = MutableSharedFlow<TransferProgress>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val flow: SharedFlow<TransferProgress> = _flow.asSharedFlow()

    private val lastEmission = AtomicLong(0L)

    suspend fun emit(progress: TransferProgress, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = lastEmission.get()
        if (!force && now - last < intervalMs) return
        if (!force && !lastEmission.compareAndSet(last, now)) return
        if (force) lastEmission.set(now)
        _flow.emit(progress)
    }
}
