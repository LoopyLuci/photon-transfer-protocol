package com.prism.ot.capture

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ImageProxy → Bitmap conversion.
 *
 * On API 30+ the CameraX pipeline is configured for
 * `OUTPUT_IMAGE_FORMAT_RGBA_8888`, which performs YUV→RGBA conversion in
 * native code on the hardware path and avoids the 25–40 ms Kotlin-side
 * conversion cost of the YUV fallback. On API 29 the
 * analyzer receives YUV_420_888 and [ImageProxy.toBitmap] performs the
 * conversion; this is slower but correct.
 *
 * The converter does not scale the full frame. Only the decodability
 * oracle needs a downscaled input; tile cropping happens in
 * [TileRectifier] so the full-resolution pixels remain available for the
 * symbol classifier.
 */
@Singleton
class FrameConverter @Inject constructor() {

    fun toBitmap(proxy: ImageProxy, rotationDegrees: Int): Bitmap {
        val raw = proxy.toBitmap()
        if (rotationDegrees == 0) return raw
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    /**
     * Downscales to the oracle's 224×224 input. Returns [source] unchanged
     * when it is already small enough, so callers must check identity
     * before recycling.
     */
    fun scaleForOracle(source: Bitmap): Bitmap {
        if (source.width <= ORACLE_SIZE && source.height <= ORACLE_SIZE) return source
        return Bitmap.createScaledBitmap(source, ORACLE_SIZE, ORACLE_SIZE, true)
    }

    companion object {
        const val ORACLE_SIZE = 224
    }
}
