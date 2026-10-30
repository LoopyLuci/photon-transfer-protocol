package com.prism.ot.motor

import com.prism.ot.core.PrismLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Serializes GATT operations on a single [android.bluetooth.BluetoothGatt].
 *
 * Why this exists: the Android platform allows only one outstanding GATT
 * operation per `BluetoothGatt` instance. Issuing a second write before the
 * first callback fires silently fails or corrupts the stack state. There is
 * no coroutine wrapper in the platform API, so every operation must be
 * bridged through `suspendCancellableCoroutine` or, as here, a channel plus
 * a deferred completion map.
 *
 * The queue is intentionally **single-producer**. The controller submits
 * operations from the policy loop and from user actions; concurrent
 * submissions are serialized by the channel's capacity of
 * [Channel.UNLIMITED] and the single consumer coroutine.
 */
internal class GattOperationQueue(
    private val logger: PrismLogger,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val timeoutMillis: Long = MotorGattSpec.OPERATION_TIMEOUT_MS,
) {

    internal sealed class Op {
        data class Write(
            val characteristicUuid: UUID,
            val value: ByteArray,
            val withoutResponse: Boolean,
        ) : Op()

        data class Read(val characteristicUuid: UUID) : Op()

        data class WriteDescriptor(
            val characteristicUuid: UUID,
            val descriptorUuid: UUID,
            val value: ByteArray,
        ) : Op()

        data object NegotiateMtu : Op()

        data object RequestHighPriority : Op()
    }

    private val channel = Channel<Queued>(Channel.UNLIMITED)
    private val pending = ConcurrentHashMap<Queued, CompletableDeferred<ByteArray?>>()
    @Volatile private var started = false

    internal data class Queued(
        val op: Op,
        val completion: CompletableDeferred<ByteArray?>,
    )

    fun start() {
        if (started) return
        started = true
        scope.launch {
            for (queued in channel) {
                val result = runCatching { awaitNative(queued) }
                result.fold(
                    onSuccess = { queued.completion.complete(it) },
                    onFailure = { queued.completion.completeExceptionally(it) },
                )
                pending.remove(queued)
            }
        }
    }

    fun stop() {
        started = false
        channel.close()
        pending.values.forEach {
            it.completeExceptionally(IllegalStateException("GATT queue stopped"))
        }
        pending.clear()
    }

    suspend fun submit(op: Op): ByteArray? {
        val completion = CompletableDeferred<ByteArray?>()
        val queued = Queued(op, completion)
        pending[queued] = completion
        channel.send(queued)
        return try {
            withTimeout(timeoutMillis) { completion.await() }
        } catch (t: TimeoutCancellationException) {
            pending.remove(queued)
            logger.w(TAG, "GATT op timed out: ${op::class.simpleName}")
            throw t
        }
    }

    /**
     * Invoked by the controller's `BluetoothGattCallback` when the native
     * operation completes. Matches the oldest pending operation of the same
     * kind and completes it.
     */
    fun completeOldest(kind: Class<out Op>, value: ByteArray? = null): Boolean {
        val entry = pending.entries.firstOrNull { kind.isInstance(it.key.op) } ?: return false
        pending.remove(entry.key)
        entry.value.complete(value)
        return true
    }

    fun failAll(reason: Throwable) {
        pending.values.forEach { it.completeExceptionally(reason) }
        pending.clear()
    }

    /**
     * Placeholder: the actual native call is issued by the controller's
     * callback bridge. This method exists so the queue's consumer has a
     * suspension point that does not block the scope.
     */
    private suspend fun awaitNative(queued: Queued): ByteArray? =
        queued.completion.await()

    companion object {
        private const val TAG = "GattOperationQueue"
    }
}
