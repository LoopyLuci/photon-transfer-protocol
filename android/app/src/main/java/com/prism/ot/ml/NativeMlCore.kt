package com.prism.ot.ml

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, stable JNI surface into the Rust ML/codec core.
 *
 * The native side is built from the `core/` crate (Batch 4) and exposes:
 *  - [version]                — semver string, used for diagnostics.
 *  - [spectreTemplateBank]    — precomputed Spectre tiling seeds for M2.
 *  - ZOI forward methods      — see [ZoiClassifierNative] and [ZoiLdpcNative].
 *
 * All native calls are expected to be fast and non-blocking. Long-running
 * native work must be dispatched by the wrappers, not by this class.
 */
@Singleton
class NativeMlCore @Inject constructor() {

    init {
        System.loadLibrary("prism_core")
    }

    external fun version(): String

    /**
     * Returns an opaque handle (pointer) to a native Spectre template bank.
     * The bank is immutable and shared across all M2 invocations. Callers
     * must not close it; it lives for the process lifetime.
     */
    external fun spectreTemplateBank(): Long

    /**
     * Applies the per-frame CIELAB affine transform in native code. This is
     * used by M4 for the fast path when the Kotlin calibrator is unavailable.
     */
    external fun applyCielabTransform(
        lab: FloatArray,
        transformMatrix: FloatArray,
    ): FloatArray
}
