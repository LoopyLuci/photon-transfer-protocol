package com.prism.ot.ml.model

sealed class ModelStatus {
    data object NotLoaded : ModelStatus()

    data class Loaded(
        val accelerator: Accelerator,
        val loadTimeMillis: Long,
    ) : ModelStatus()

    data class Failed(
        val reason: String,
        val recoverable: Boolean,
    ) : ModelStatus()
}

enum class Accelerator {
    GPU,
    CPU_XNNPACK,
    ZERO_OVERHEAD_NATIVE,
    CPU_REFERENCE,
    ;

    val displayName: String
        get() = when (this) {
            GPU -> "GPU"
            CPU_XNNPACK -> "CPU (XNNPACK)"
            ZERO_OVERHEAD_NATIVE -> "Native ZOI"
            CPU_REFERENCE -> "CPU (reference)"
        }
}
