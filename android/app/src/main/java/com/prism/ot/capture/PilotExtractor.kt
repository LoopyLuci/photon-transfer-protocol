package com.prism.ot.capture

import android.graphics.Bitmap
import com.prism.ot.codec.Alphabet
import com.prism.ot.ml.ChromaticPilot

/**
 * Extracts chromatic calibration pilots from a rectified tile.
 *
 * Contract with the encoder: every PRISM tile carries four pilot patches
 * at the normalized positions in [POSITIONS], painted with the first four
 * entries of [Alphabet.DENSE_64_COLOURS] in the same order. The encoder
 * renders these patches as part of the tile header; the decoder reads them
 * to learn the per-frame CIELAB transform in M4.
 *
 * This is intentionally a fixed contract rather than a discovered layout.
 * A discovered layout would require a second detection pass and would make
 * the pilot positions ambiguous under partial occlusion.
 */
object PilotExtractor {

    private val POSITIONS = listOf(
        0.12f to 0.12f,   // top-left   → colour 0
        0.88f to 0.12f,   // top-right  → colour 1
        0.12f to 0.88f,   // bottom-left→ colour 2
        0.88f to 0.88f,   // bottom-right→colour 3
    )

    fun extract(rectified: Bitmap): List<ChromaticPilot> =
        POSITIONS.mapIndexed { index, (nx, ny) ->
            val x = (nx * rectified.width).toInt().coerceIn(0, rectified.width - 1)
            val y = (ny * rectified.height).toInt().coerceIn(0, rectified.height - 1)
            val pixel = rectified.getPixel(x, y)
            val intended = Alphabet.DENSE_64_COLOURS[index % Alphabet.DENSE_64_COLOURS.size]
            ChromaticPilot(
                intendedL = intended[0],
                intendedA = intended[1],
                intendedB = intended[2],
                receivedR = (pixel shr 16) and 0xFF,
                receivedG = (pixel shr 8) and 0xFF,
                receivedB = pixel and 0xFF,
            )
        }
}
