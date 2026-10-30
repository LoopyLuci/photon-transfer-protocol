package com.prism.ot.ml.model

import android.graphics.Bitmap

// ---- M1 ----
data class DecodabilityScore(
    val score: Float,                 // [0,1], 1 = very likely decodable
    val blurEstimateSigmaPx: Float,
    val contrastEstimate: Float,
    val occlusionEstimate: Float,     // [0,1], 1 = heavily occluded
) {
    val isWorthDecoding: Boolean get() = score >= 0.55f
}

// ---- M2 ----
data class AperiodicTileDetection(
    val centroidX: Float,             // normalised [0,1] in frame
    val centroidY: Float,
    val orientationRadians: Float,
    val scaleFraction: Float,         // tile size as fraction of frame min-dim
    val confidence: Float,
    val vertexXs: FloatArray,         // Spectre tile vertices, normalised
    val vertexYs: FloatArray,
    val clusterLevel: Int,            // for hierarchical template matching
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AperiodicTileDetection) return false
        return centroidX == other.centroidX &&
            centroidY == other.centroidY &&
            orientationRadians == other.orientationRadians &&
            scaleFraction == other.scaleFraction &&
            confidence == other.confidence &&
            vertexXs.contentEquals(other.vertexXs) &&
            vertexYs.contentEquals(other.vertexYs) &&
            clusterLevel == other.clusterLevel
    }

    override fun hashCode(): Int {
        var result = centroidX.hashCode()
        result = 31 * result + centroidY.hashCode()
        result = 31 * result + orientationRadians.hashCode()
        result = 31 * result + scaleFraction.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + vertexXs.contentHashCode()
        result = 31 * result + vertexYs.contentHashCode()
        result = 31 * result + clusterLevel
        return result
    }
}

// ---- M3 ----
data class SuperResolutionResult(
    val upscaled: Bitmap,
    val scaleFactor: Int,
    val confidence: Float,
    val decodePreserved: Boolean,     // true if decode-in-the-loop loss passed
)

// ---- M5 ----
data class SoftSymbolProbabilities(
    val probabilities: FloatArray,    // length = alphabet size
    val alphabetSize: Int,
    val entropy: Float,               // normalised [0,1], 1 = uniform
    val topSymbolIndex: Int,
    val topSymbolConfidence: Float,
    val classificationTimeMicros: Long,
) {
    init {
        require(probabilities.size == alphabetSize) {
            "probabilities length ${probabilities.size} != alphabetSize $alphabetSize"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SoftSymbolProbabilities) return false
        return probabilities.contentEquals(other.probabilities) &&
            alphabetSize == other.alphabetSize &&
            entropy == other.entropy &&
            topSymbolIndex == other.topSymbolIndex &&
            topSymbolConfidence == other.topSymbolConfidence &&
            classificationTimeMicros == other.classificationTimeMicros
    }

    override fun hashCode(): Int {
        var result = probabilities.contentHashCode()
        result = 31 * result + alphabetSize
        result = 31 * result + entropy.hashCode()
        result = 31 * result + topSymbolIndex
        result = 31 * result + topSymbolConfidence.hashCode()
        result = 31 * result + classificationTimeMicros.hashCode()
        return result
    }
}

// ---- M7 ----
data class FountainPolicy(
    val distributionId: Int,          // index into the firmware's degree-distribution bank
    val targetOverhead: Float,        // e.g. 1.25
    val recommendedSegmentBytes: Int,
    val confidence: Float,
)

// ---- M8 ----
data class FaceTrackState(
    val massBoundingBox: FloatArray,  // [left, top, right, bottom] normalised
    val visibleFaceIds: List<Int>,
    val visibleFaceProbabilities: FloatArray,
    val rotationPhaseRadians: Float,
    val angularVelocityRadPerSec: Float,
    val blurSigmaByFace: FloatArray,
    val trackingConfidence: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceTrackState) return false
        return massBoundingBox.contentEquals(other.massBoundingBox) &&
            visibleFaceIds == other.visibleFaceIds &&
            visibleFaceProbabilities.contentEquals(other.visibleFaceProbabilities) &&
            rotationPhaseRadians == other.rotationPhaseRadians &&
            angularVelocityRadPerSec == other.angularVelocityRadPerSec &&
            blurSigmaByFace.contentEquals(other.blurSigmaByFace) &&
            trackingConfidence == other.trackingConfidence
    }

    override fun hashCode(): Int {
        var result = massBoundingBox.contentHashCode()
        result = 31 * result + visibleFaceIds.hashCode()
        result = 31 * result + visibleFaceProbabilities.contentHashCode()
        result = 31 * result + rotationPhaseRadians.hashCode()
        result = 31 * result + angularVelocityRadPerSec.hashCode()
        result = 31 * result + blurSigmaByFace.contentHashCode()
        result = 31 * result + trackingConfidence.hashCode()
        return result
    }
}

// ---- M9 ----
data class DeblurResult(
    val deblurred: Bitmap,
    val estimatedKernelEnergyReduction: Float,
    val confidence: Float,
)

// ---- M12 ----
data class PrintDefectReport(
    val isPrintable: Boolean,
    val defects: List<PrintDefect>,
    val overallConfidence: Float,
)

data class PrintDefect(
    val type: PrintDefectType,
    val severity: Float,              // [0,1]
    val boundingBoxNormalised: FloatArray,
)

enum class PrintDefectType {
    INK_BLEED,
    COLOR_MISREGISTRATION,
    MISSING_TILE,
    SMUDGE,
    FOLD_CREASE,
    SPECULAR_HOTSPOT,
    UNKNOWN,
}
