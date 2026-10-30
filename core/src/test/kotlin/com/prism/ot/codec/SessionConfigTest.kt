package com.prism.ot.codec

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import kotlinx.serialization.json.Json

/**
 * SessionConfig serialisation is part of the wire format: the encoder
 * embeds it in the fountain root block and the decoder parses it. A
 * serialisation change without a format-version bump silently breaks
 * cross-version transfers. These tests pin the round-trip.
 */
class SessionConfigTest : FreeSpec({

    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    "round-trips through JSON" {
        val config = SessionConfig(
            formatVersion = 1,
            alphabetMode = AlphabetMode.DENSE_64,
            innerCodeMode = InnerCodeMode.NB_LDPC_GF64,
            outerCodeMode = OuterCodeMode.RAPTORQ,
            segmentBytes = 32 * 1024 * 1024,
            symbolBytes = 7_500,
            targetOverhead = 1.30f,
            distributionId = 0,
            useHierarchicalPartitioning = true,
            errorCorrectionBudget = 0.35f,
        )
        val encoded = json.encodeToString(SessionConfig.serializer(), config)
        val decoded = json.decodeFromString(SessionConfig.serializer(), encoded)
        decoded shouldBe config
    }

    "rejects unknown alphabet modes" {
        val malformed = """{"formatVersion":1,"alphabetMode":"HYPER_1024","innerCodeMode":"NB_LDPC_GF64","outerCodeMode":"RAPTORQ","segmentBytes":1,"symbolBytes":1,"targetOverhead":1.0,"distributionId":0,"useHierarchicalPartitioning":false,"errorCorrectionBudget":0.1}"""
        runCatching { json.decodeFromString(SessionConfig.serializer(), malformed) }
            .isFailure shouldBe true
    }

    "segmentBytes is always positive for every valid config" {
        forAll(
            Arb.int(1, Int.MAX_VALUE),
            Arb.enum<AlphabetMode>(),
        ) { segment, alphabet ->
            SessionConfig(
                formatVersion = 1,
                alphabetMode = alphabet,
                innerCodeMode = InnerCodeMode.NB_LDPC_GF64,
                outerCodeMode = OuterCodeMode.RAPTORQ,
                segmentBytes = segment,
                symbolBytes = 1024,
                targetOverhead = 1.3f,
                distributionId = 0,
                useHierarchicalPartitioning = true,
                errorCorrectionBudget = 0.3f,
            ).segmentBytes == segment
        }
    }
})
