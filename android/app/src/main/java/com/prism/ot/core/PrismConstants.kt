package com.prism.ot.core

/**
 * Single source of truth for tunables. Any constant that influences the
 * on-disk format, wire format, or fountain code semantics is frozen here
 * and versioned via [FORMAT_VERSION].
 */
object PrismConstants {

    // ---- Format ----
    const val FORMAT_VERSION: Int = 1
    const val MIN_SUPPORTED_FORMAT_VERSION: Int = 1

    // ---- Static mode ----
    const val STATIC_TILE_PIXELS: Int = 1024
    const val STATIC_TILE_BYTES: Int = 7_500              // conservative Dense alphabet
    const val STATIC_TILE_BYTES_DENSE_PLUS: Int = 12_000
    const val STATIC_DEFAULT_DPI: Int = 600
    const val STATIC_MAX_DPI: Int = 1200
    const val STATIC_TILES_PER_PAGE: Int = 30
    const val STATIC_METADATA_BAND_HEIGHT_PX: Int = 220
    const val STATIC_PAGE_A4_WIDTH_MM: Float = 210f
    const val STATIC_PAGE_A4_HEIGHT_MM: Float = 297f

    // ---- Kinetic mode ----
    const val KINETIC_MIN_FACE_BYTES: Int = 4_096
    const val KINETIC_DEFAULT_FACE_BYTES: Int = 7_500
    const val KINETIC_MAX_FACE_BYTES: Int = 24_576
    const val KINETIC_MIN_FACES: Int = 8
    const val KINETIC_DEFAULT_FACES: Int = 16
    const val KINETIC_MAX_FACES: Int = 32
    const val KINETIC_FACE_RATE_TOLERANCE: Float = 0.85f    // must stay below min fps
    const val KINETIC_MIN_CAMERA_FPS: Int = 8
    const val KINETIC_MAX_CAMERA_FPS: Int = 120
    const val KINETIC_DEFAULT_ROTATION_HZ: Float = 0.25f
    const val KINETIC_MAX_ROTATION_HZ: Float = 1.0f

    // ---- Fountain / transfer ----
    const val FOUNTAIN_SEGMENT_BYTES: Int = 32 * 1024 * 1024       // 32 MiB
    const val FOUNTAIN_TARGET_OVERHEAD: Float = 1.30f
    const val FOUNTAIN_MIN_OVERHEAD: Float = 1.10f
    const val FOUNTAIN_MAX_OVERHEAD: Float = 1.80f
    const val FOUNTAIN_PACKET_HEADER_BYTES: Int = 24

    // ---- Camera ----
    const val TARGET_CAPTURE_FPS: Int = 60
    const val FALLBACK_CAPTURE_FPS: Int = 30
    const val ANALYSIS_TARGET_WIDTH: Int = 1280
    const val ANALYSIS_TARGET_HEIGHT: Int = 720

    // ---- ML ----
    const val MODEL_INPUT_SIZE: Int = 224
    const val MODEL_TILE_INPUT_SIZE: Int = 32
    const val MODEL_DECISION_THRESHOLD: Float = 0.55f
    const val MODEL_TEMPERATURE: Float = 1.20f             // softmax temperature for M5
    const val CHANNEL_STATE_HISTORY: Int = 16
    const val SYMBOL_ALPHABET_SIZE_DENSE: Int = 64          // 16 shapes × 4 colours
    const val SYMBOL_ALPHABET_SIZE_DENSE_PLUS: Int = 128    // 16 shapes × 8 colours
    const val SYMBOL_ALPHABET_SIZE_READABLE: Int = 52

    // ---- Integrity ----
    const val SHA256_HEX_LENGTH: Int = 64
    const val SHA256_BUFFER_BYTES: Int = 64 * 1024

    // ---- File handling ----
    const val RECEIVED_DIR: String = "received"
    const val TEMP_DIR: String = "incoming"
    const val MAX_CONCURRENT_DECODERS: Int = 3
    const val PROGRESS_EMIT_INTERVAL_MS: Long = 150L

    // ---- Session ----
    const val SESSION_ID_BYTES: Int = 16
    const val SESSION_TIMEOUT_MS: Long = 30L * 60L * 1000L      // 30 minutes
    const val SESSION_KEEP_ALIVE_MS: Long = 5L * 60L * 1000L    // 5 minutes

    // ---- BLE motor ----
    const val MOTOR_SERVICE_UUID: String = "0000prism-0000-1000-8000-00805f9b34fb"
    const val MOTOR_CHAR_SPEED_UUID: String = "0000pris1-0000-1000-8000-00805f9b34fb"
    const val MOTOR_CHAR_TELEMETRY_UUID: String = "0000pris2-0000-1000-8000-00805f9b34fb"
    const val MOTOR_RECONNECT_ATTEMPTS: Int = 3
}
