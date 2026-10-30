package com.prism.ot.data.model

import androidx.camera.core.ImageProxy

/**
 * Lightweight wrapper around a camera frame with the metadata the decoder
 * needs, decoupled from CameraX's ImageProxy lifecycle.
 *
 * The [imageProxy] field is only valid for the duration of the analysis
 * callback; consumers must copy or process synchronously.
 */
data class FrameData(
    val imageProxy: ImageProxy,
    val timestampNanos: Long,
    val rotationDegrees: Int,
    val width: Int,
    val height: Int,
    val estimatedFps: Float,
    val isFromStrobeSync: Boolean = false,
)
