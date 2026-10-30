package com.prism.ot.motor

/**
 * Explicit connection lifecycle. The state machine is deliberately small
 * and monotonic in the forward direction; every failure collapses back to
 * [Disconnected] with a reason so the UI can surface an actionable message.
 *
 * Idle → Scanning → Connecting → DiscoveringServices → Ready
 *                                                         ↓
 *                                                    Reconnecting
 *                                                         ↓
 *                                                    Disconnected
 */
sealed class MotorConnectionState {

    data object Idle : MotorConnectionState()

    data object Scanning : MotorConnectionState()

    data class Connecting(val macAddress: String) : MotorConnectionState()

    data class DiscoveringServices(val macAddress: String) : MotorConnectionState()

    data class Ready(
        val macAddress: String,
        val mtu: Int,
        val connectionPriority: Priority,
        val negotiatedRotationHz: Float,
    ) : MotorConnectionState()

    data class Reconnecting(
        val macAddress: String,
        val attempt: Int,
        val nextDelayMillis: Long,
    ) : MotorConnectionState()

    data class Disconnected(
        val macAddress: String?,
        val reason: Reason,
    ) : MotorConnectionState() {
        enum class Reason {
            USER_REQUEST,
            GATT_STATUS_ERROR,
            TIMEOUT,
            BLUETOOTH_OFF,
            PERMISSION_DENIED,
            UNKNOWN,
        }
    }

    data class Failed(
        val macAddress: String?,
        val error: com.prism.ot.core.PrismError,
    ) : MotorConnectionState()

    enum class Priority { BALANCED, HIGH }

    val isConnected: Boolean get() = this is Ready
}
