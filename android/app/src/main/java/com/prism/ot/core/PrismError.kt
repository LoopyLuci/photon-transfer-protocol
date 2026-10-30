package com.prism.ot.core

/**
 * Exhaustive error taxonomy. Every layer returns one of these rather than
 * raw exceptions so the UI can render an actionable message and analytics
 * can bucket failures consistently.
 */
sealed class PrismError(val userMessage: String, val code: String) {

    // ---- Camera / capture ----
    data object CameraPermissionDenied :
        PrismError("Camera permission is required to scan.", "CAM_PERM")

    data object CameraUnavailable :
        PrismError("Camera is not available or in use by another app.", "CAM_UNAVAIL")

    data class CameraInitializationFailed(val detail: String) :
        PrismError("Camera failed to start: $detail", "CAM_INIT")

    data class CameraFrameDropped(val count: Long) :
        PrismError("$count frames were dropped by the camera.", "CAM_DROP")

    // ---- Codec / symbol ----
    data object NativeLibraryMissing :
        PrismError("PRISM native core is missing from this build.", "CODEC_NATIVE")

    data class CodecInitializationFailed(val detail: String) :
        PrismError("Codec initialisation failed: $detail", "CODEC_INIT")

    data class InvalidTile(val reason: String) :
        PrismError("A code tile could not be decoded: $reason", "CODEC_TILE")

    data class UnsupportedSymbolVersion(val version: Int) :
        PrismError("This code uses a newer PRISM format (v$version).", "CODEC_VERSION")

    data object NoTilesDetected :
        PrismError("No PRISM code was detected in the frame.", "CODEC_NOTILES")

    // ---- Fountain / transfer ----
    data class FountainDecodeFailed(val reason: String) :
        PrismError("Fountain decoding stalled: $reason", "FNT_STALL")

    data class IntegrityMismatch(val expectedSha256: String, val actualSha256: String) :
        PrismError(
            "File integrity check failed. The transfer was corrupted and discarded.",
            "FNT_HASH"
        )

    data class InsufficientPackets(val collected: Int, val required: Int) :
        PrismError(
            "Collected $collected of $required packets. Keep scanning.",
            "FNT_PACKETS"
        )

    data object TransferCancelled :
        PrismError("Transfer was cancelled.", "FNT_CANCEL")

    // ---- I/O ----
    data class FileReadFailed(val detail: String) :
        PrismError("Could not read the source file: $detail", "IO_READ")

    data class FileWriteFailed(val detail: String) :
        PrismError("Could not write the received file: $detail", "IO_WRITE")

    data object InsufficientStorage :
        PrismError("Not enough free storage for this transfer.", "IO_STORAGE")

    // ---- Motor / kinetic ----
    data object BluetoothUnavailable :
        PrismError("Bluetooth is required to control the rotating mass.", "MOTOR_BT")

    data class MotorConnectionFailed(val detail: String) :
        PrismError("Could not connect to the rotating mass: $detail", "MOTOR_CONN")

    data class MotorCommandFailed(val detail: String) :
        PrismError("Motor command failed: $detail", "MOTOR_CMD")

    // ---- ML ----
    data class ModelLoadFailed(val modelId: String, val detail: String) :
        PrismError("AI model '$modelId' failed to load: $detail", "ML_LOAD")

    data class ModelInferenceFailed(val modelId: String, val detail: String) :
        PrismError("AI model '$modelId' inference error: $detail", "ML_INFER")

    // ---- UI / flow ----
    data object UserAborted :
        PrismError("Operation cancelled by the user.", "USER_ABORT")

    data class Unexpected(val detail: String) :
        PrismError("Unexpected error: $detail", "UNEXPECTED")
}
