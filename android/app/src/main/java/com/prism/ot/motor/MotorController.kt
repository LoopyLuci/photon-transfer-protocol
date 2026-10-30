package com.prism.ot.motor

import com.prism.ot.core.PrismResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Public motor façade. All methods are safe to call from any dispatcher;
 * the implementation serializes GATT operations internally.
 *
 * The controller does not scan. It connects to a known MAC address that the
 * user selected (or that was persisted from a previous session). Scanning
 * is exposed as a separate one-shot flow in [scanForMotors].
 */
interface MotorController {

    val connectionState: StateFlow<MotorConnectionState>

    /** Latest telemetry, or null if none has arrived yet. */
    val telemetry: StateFlow<MotorTelemetry?>

    /** One-shot scan for nearby PRISM motor peripherals. */
    suspend fun scanForMotors(timeoutMillis: Long = 5_000L): PrismResult<List<DiscoveredMotor>>

    suspend fun connect(macAddress: String): PrismResult<Unit>

    suspend fun disconnect()

    /** Sets target rotation in Hz. Fire-and-forget; returns once queued. */
    suspend fun setTargetRotationHz(hz: Float): PrismResult<Unit>

    suspend fun sendControl(command: Byte): PrismResult<Unit>

    suspend fun requestHighThroughput(): PrismResult<Unit>
}

data class DiscoveredMotor(
    val macAddress: String,
    val displayName: String,
    val rssi: Int,
)

data class MotorTelemetry(
    val actualRotationHz: Float,
    val currentMilliAmps: Long,
    val deviceTimestampMillis: Long,
    val receivedAtEpochMillis: Long,
)
