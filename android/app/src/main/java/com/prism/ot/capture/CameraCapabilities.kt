package com.prism.ot.capture

/**
 * Raw capability snapshot extracted from Camera2 characteristics.
 * Kept separate from [CameraSessionInfo] because it describes what the
 * device *can* do, not what was actually selected.
 */
data class CameraCapabilities(
    val supportedFpsRanges: List<IntRange>,
    val sensorOrientationDegrees: Int,
)
