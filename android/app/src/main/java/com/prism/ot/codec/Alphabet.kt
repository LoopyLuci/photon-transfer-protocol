package com.prism.ot.codec

/**
 * Chromatic–Geometric–Glyph alphabet.
 *
 * A symbol is an index in [0, alphabetSize). The index decomposes as:
 *
 *   index = shapeIndex * colourCount + colourIndex
 *
 * Shapes are geometric primitives rendered inside a square cell. Colours are
 * CIELAB coordinates calibrated per frame by M4. The readable mode uses
 * alphanumeric glyphs instead of shapes, with a fixed mapping.
 *
 * The native encoder consumes [shapeTable] and [colourTable] to render
 * tiles; the native decoder receives the same tables so that symbol
 * indices are interpreted identically on both sides.
 */
object Alphabet {

    // ---- Dense 64: 16 shapes × 4 colours ----
    val DENSE_64_SHAPES: Array<FloatArray> = arrayOf(
        // Each shape is an 8×8 binary template, row-major.
        // 0: solid square
        floatArrayOf(
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        ),
        // 1: hollow square (border only)
        floatArrayOf(
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        ),
        // 2: diagonal stripe
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
        ),
        // 3: anti-diagonal stripe
        floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        ),
        // 4: horizontal bars
        floatArrayOf(
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        ),
        // 5: vertical bars
        floatArrayOf(
            1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f,
            1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f,
            1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f,
            1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f,
            1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f,
            1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f,
            1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f,
            1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f,
        ),
        // 6: checkerboard
        floatArrayOf(
            1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f,
            1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f,
            1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f,
            1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f,
        ),
        // 7: cross
        floatArrayOf(
            0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f,
        ),
        // 8: top half
        floatArrayOf(
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        ),
        // 9: bottom half
        floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        ),
        // 10: left half
        floatArrayOf(
            1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f,
            1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f,
        ),
        // 11: right half
        floatArrayOf(
            0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
        ),
        // 12: X
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f,
            0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
        ),
        // 13: circle (approximated)
        floatArrayOf(
            0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f,
            0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f,
            1f, 1f, 0f, 0f, 0f, 0f, 1f, 1f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
            1f, 1f, 0f, 0f, 0f, 0f, 1f, 1f,
            0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f,
        ),
        // 14: diamond
        floatArrayOf(
            0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 1f, 0f,
            0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            0f, 0f, 1f, 1f, 1f, 1f, 1f, 0f,
            0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f,
        ),
        // 15: dot (center only)
        floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        ),
    )

    /**
     * Dense 64 colour table: 4 CIELAB centres. Values are chosen to
     * maximise ΔE00 separation within the screen-camera gamut intersection.
     * The native renderer converts these to sRGB using the per-frame
     * calibration from M4.
     */
    val DENSE_64_COLOURS: Array<FloatArray> = arrayOf(
        floatArrayOf(52.0f, 40.0f, 18.0f),    // dark red
        floatArrayOf(48.0f, -28.0f, 42.0f),   // blue
        floatArrayOf(88.0f, -18.0f, 78.0f),   // light green
        floatArrayOf(62.0f, 32.0f, -58.0f),   // purple-blue
    )

    // ---- Dense+ 128: 16 shapes × 8 colours ----
    val DENSE_PLUS_128_COLOURS: Array<FloatArray> = arrayOf(
        floatArrayOf(52.0f, 40.0f, 18.0f),
        floatArrayOf(48.0f, -28.0f, 42.0f),
        floatArrayOf(88.0f, -18.0f, 78.0f),
        floatArrayOf(62.0f, 32.0f, -58.0f),
        floatArrayOf(30.0f, 18.0f, -42.0f),
        floatArrayOf(78.0f, 22.0f, 68.0f),
        floatArrayOf(42.0f, -42.0f, -18.0f),
        floatArrayOf(70.0f, 8.0f, -22.0f),
    )

    // ---- Readable 52: digits + letters + shapes ----
    val READABLE_GLYPHS: Array<Char> = (
        ('0'..'9') + ('A'..'Z') + ('a'..'z') + ('#'..'&')
    ).toList().take(52).toTypedArray()

    fun alphabetSize(mode: AlphabetMode): Int = when (mode) {
        AlphabetMode.DENSE_64 -> DENSE_64_SHAPES.size * DENSE_64_COLOURS.size
        AlphabetMode.DENSE_PLUS_128 -> DENSE_64_SHAPES.size * DENSE_PLUS_128_COLOURS.size
        AlphabetMode.READABLE_52 -> READABLE_GLYPHS.size
    }

    fun bitsPerCell(mode: AlphabetMode): Int =
        Integer.SIZE - Integer.numberOfLeadingZeros(alphabetSize(mode) - 1)
}
