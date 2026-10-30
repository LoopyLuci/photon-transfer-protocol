package com.prism.ot.motor

import java.util.UUID

/**
 * Wire contract between the phone and the rotating mass's BLE MCU.
 *
 * The MCU firmware is expected to implement:
 *  - Speed characteristic: **write without response**, 4 bytes little-endian
 *    IEEE-754 float, target rotation in Hz. Write-without-response is used
 *    because the motor loop runs at 5–20 Hz and waiting for a write
 *    acknowledgement would halve the achievable control rate.
 *  - Telemetry characteristic: **notify**, 12 bytes:
 *        [0..3]  actual rotation Hz (f32 LE)
 *        [4..7]  motor current mA     (u32 LE)
 *        [8..11] timestamp millis     (u32 LE)
 *  - Control characteristic: **write with response**, 1 byte command:
 *        0x01 stop, 0x02 run, 0x03 calibrate, 0x04 LED on, 0x05 LED off.
 *
 * UUIDs are fixed for format version 1 and are referenced from
 * [com.prism.ot.core.PrismConstants].
 */
object MotorGattSpec {

    val SERVICE: UUID = UUID.fromString(
        "0000a000-0000-1000-8000-00805f9b34fb"
    )
    val CHAR_SPEED: UUID = UUID.fromString(
        "0000a001-0000-1000-8000-00805f9b34fb"
    )
    val CHAR_TELEMETRY: UUID = UUID.fromString(
        "0000a002-0000-1000-8000-00805f9b34fb"
    )
    val CHAR_CONTROL: UUID = UUID.fromString(
        "0000a003-0000-1000-8000-00805f9b34fb"
    )
    val CCCD: UUID = UUID.fromString(
        "00002902-0000-1000-8000-00805f9b34fb"
    )

    const val SPEED_PAYLOAD_BYTES = 4
    const val TELEMETRY_PAYLOAD_BYTES = 12

    const val CMD_STOP: Byte = 0x01
    const val CMD_RUN: Byte = 0x02
    const val CMD_CALIBRATE: Byte = 0x03
    const val CMD_LED_ON: Byte = 0x04
    const val CMD_LED_OFF: Byte = 0x05

    /** Desired MTU. 517 is the maximum common value on modern Android. */
    const val TARGET_MTU = 517

    /** Default MTU if negotiation fails (BLE minimum is 23). */
    const val FALLBACK_MTU = 23

    /** Per-operation timeout. Motor commands must never hang the loop. */
    const val OPERATION_TIMEOUT_MS = 4_000L

    /** Service discovery must complete within this window. */
    const val DISCOVERY_TIMEOUT_MS = 10_000L

    /** Heartbeat interval to keep the link active and prevent the OS from
     *  widening the connection interval during idle periods. */
    const val HEARTBEAT_INTERVAL_MS = 5_000L
}
