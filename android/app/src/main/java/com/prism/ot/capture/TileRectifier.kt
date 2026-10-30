package com.prism.ot.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.prism.ot.ml.model.AperiodicTileDetection

/**
 * Rectifies a detected Spectre tile into a square bitmap suitable for M5.
 *
 * Two paths:
 *  1. If the detector returned exactly four vertices, a projective
 *     four-point transform is applied. This is the preferred path for
 *     square tiles and for Spectre tiles whose convex hull is
 *     quadrilateral.
 *  2. Otherwise, the axis-aligned bounding box is cropped with 10%
 *     padding and scaled. M5 is trained on rotated and partially cropped
 *     tiles, so this fallback remains decodable even though it wastes some
 *     pixels.
 *
 * The returned bitmap is always newly allocated. The caller owns it and
 * must recycle it.
 */
object TileRectifier {

    fun rectify(
        frame: Bitmap,
        detection: AperiodicTileDetection,
        outputSize: Int = 64,
    ): Bitmap {
        val xs = detection.vertexXs
        val ys = detection.vertexYs

        if (xs.size == 4 && ys.size == 4) {
            val src = floatArrayOf(
                xs[0] * frame.width, ys[0] * frame.height,
                xs[1] * frame.width, ys[1] * frame.height,
                xs[2] * frame.width, ys[2] * frame.height,
                xs[3] * frame.width, ys[3] * frame.height,
            )
            val dst = floatArrayOf(
                0f, 0f,
                outputSize.toFloat(), 0f,
                outputSize.toFloat(), outputSize.toFloat(),
                0f, outputSize.toFloat(),
            )
            val matrix = Matrix()
            if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
                val out = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
                Canvas(out).drawBitmap(frame, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                return out
            }
        }

        return boundingBoxCrop(frame, detection, outputSize)
    }

    private fun boundingBoxCrop(
        frame: Bitmap,
        detection: AperiodicTileDetection,
        outputSize: Int,
    ): Bitmap {
        val xs = detection.vertexXs.map { it * frame.width }
        val ys = detection.vertexYs.map { it * frame.height }
        val minX = (xs.minOrNull() ?: 0f).toInt().coerceIn(0, frame.width - 1)
        val maxX = (xs.maxOrNull() ?: frame.width.toFloat()).toInt().coerceIn(0, frame.width)
        val minY = (ys.minOrNull() ?: 0f).toInt().coerceIn(0, frame.height - 1)
        val maxY = (ys.maxOrNull() ?: frame.height.toFloat()).toInt().coerceIn(0, frame.height)

        val padX = ((maxX - minX) * PADDING_FRACTION).toInt()
        val padY = ((maxY - minY) * PADDING_FRACTION).toInt()
        val left = (minX - padX).coerceIn(0, frame.width - 1)
        val top = (minY - padY).coerceIn(0, frame.height - 1)
        val right = (maxX + padX).coerceIn(left + 1, frame.width)
        val bottom = (maxY + padY).coerceIn(top + 1, frame.height)

        val cropped = Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
        val scaled = Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)
        if (cropped !== scaled) cropped.recycle()
        return scaled
    }

    private const val PADDING_FRACTION = 0.10f
}
