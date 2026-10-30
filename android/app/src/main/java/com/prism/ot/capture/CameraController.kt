package com.prism.ot.capture

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.TransferMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-bound CameraX façade for PRISM scanning.
 *
 * Contract:
 *  - [bind] is idempotent: a second call unbinds the previous session first.
 *  - The controller always negotiates a fixed frame-rate range when one is
 *    available. Variable ranges (e.g. 15–30) are only chosen when no fixed
 *    range exists, because PRISM's face-rate matching in kinetic mode
 *    depends on a stable frame period.
 *  - The analyzer executor is a dedicated background pool. The controller
 *    never touches the main thread for frame processing.
 */
interface CameraController {

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: FrameAnalyzer,
        mode: TransferMode,
    ): PrismResult<CameraSessionInfo>

    fun unbind()

    fun setTorch(enabled: Boolean)

    fun setZoom(ratio: Float)

    suspend fun focusAt(normalizedX: Float, normalizedY: Float): PrismResult<Unit>

    val sessionInfo: StateFlow<CameraSessionInfo?>

    /** Measured capture rate, updated once per frame. */
    val actualFps: StateFlow<Float>
}

/**
 * Immutable snapshot of what the camera actually negotiated.
 *
 * [rollingShutterSkewNanos] is the value reported by
 * `CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW`: the duration between the
 * start of exposure of the first sensor row and the start of exposure one
 * row past the last. It is the basis for every skew decision in
 * [RollingShutterEstimator].
 */
data class CameraSessionInfo(
    val selectedFpsRange: IntRange,
    val supportedFpsRanges: List<IntRange>,
    val sensorOrientationDegrees: Int,
    val rollingShutterSkewNanos: Long,
    val isRgbaOutput: Boolean,
) {
    val nominalFps: Int get() = selectedFpsRange.upper
    val isFixedFrameRate: Boolean get() = selectedFpsRange.first == selectedFpsRange.last
}
