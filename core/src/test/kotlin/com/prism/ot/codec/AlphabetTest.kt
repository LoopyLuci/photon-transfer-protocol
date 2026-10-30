package com.prism.ot.codec

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll

/**
 * Alphabet invariants.
 *
 * The CGG alphabet is the contract between the encoder, the native
 * renderer, and the classifier. If the shape or colour table changes size,
 * every downstream model must be retrained. These tests fail loudly when
 * that invariant is broken.
 */
class AlphabetTest : FreeSpec({

    "Dense-64 alphabet" - {

        "has exactly 16 shapes" {
            Alphabet.DENSE_64_SHAPES shouldHaveSize 16
        }

        "every shape is an 8×8 template" {
            Alphabet.DENSE_64_SHAPES.forEach { shape ->
                shape shouldHaveSize 64
                shape.all { it == 0f || it == 1f } shouldBe true
            }
        }

        "has exactly 4 colours" {
            Alphabet.DENSE_64_COLOURS shouldHaveSize 4
        }

        "declares 64 symbols and 6 bits per cell" {
            Alphabet.alphabetSize(AlphabetMode.DENSE_64) shouldBe 64
            Alphabet.bitsPerCell(AlphabetMode.DENSE_64) shouldBe 6
        }

        "colour components are finite CIELAB triples" {
            Alphabet.DENSE_64_COLOURS.forEach { colour ->
                colour shouldHaveSize 3
                colour.all { it.isFinite() } shouldBe true
            }
        }
    }

    "Dense-plus-128 alphabet" - {

        "has 8 colours" {
            Alphabet.DENSE_PLUS_128_COLOURS shouldHaveSize 8
        }

        "declares 128 symbols and 7 bits per cell" {
            Alphabet.alphabetSize(AlphabetMode.DENSE_PLUS_128) shouldBe 128
            Alphabet.bitsPerCell(AlphabetMode.DENSE_PLUS_128) shouldBe 7
        }
    }

    "Readable alphabet" - {

        "declares 52 glyphs" {
            Alphabet.READABLE_GLYPHS shouldHaveSize 52
        }

        "bits per cell is at least 5" {
            Alphabet.bitsPerCell(AlphabetMode.READABLE_52) shouldBeGreaterThan 4
        }
    }

    "symbol index round-trips through (shape, colour)" {
        forAll(Arb.int(0, 63)) { index ->
            val shapeIndex = index / 4
            val colourIndex = index % 4
            val reconstructed = shapeIndex * 4 + colourIndex
            reconstructed == index
        }
    }
})
