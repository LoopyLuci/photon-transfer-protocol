package com.prism.ot.motor

import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.ml.MlBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Closed-loop motor control.
 *
 * Every [INTERVAL_MS] the loop:
 *  1. Reads the measured camera FPS and the recent face-decode rate.
 *  2. Asks M10 (the zero-overhead native policy) for a suggested rotation
 *     frequency.
 *  3. Clamps the suggestion through [FaceRateMatcher] so a misbehaving
 *     policy cannot command a speed that guarantees frame loss.
 *  4. Writes the clamped value to the motor via [MotorController].
 *
 * The loop runs at 10 Hz. Motor telemetry arrives at the same rate, so the
 * policy always sees a value at most one control period old. A faster loop
 * would fight the OS power manager and add BLE writes that the motor's
 * 50 Hz PWM stage cannot meaningfully act on.
 */
@Singleton
class MotorPolicyLoop @Inject constructor(
    private val motorController: MotorController,
    private val faceRateMatcher: FaceRateMatcher,
    private val mlBridge: MlBridge,
    private val logger: PrismLogger,
) {

    private val _policyState = MutableStateFlow(MotorPolicyState())
    val policyState: StateFlow<MotorPolicyState> = _policyState.asStateFlow()

    private var loopJob: Job? = null

    fun start(
        scope: CoroutineScope,
        faceCount: Int,
        cameraFpsProvider: () -> Float,
        decodeRateProvider: () -> Float,
        blurProvider: () -> Float,
    ) {
        stop()
        loopJob = scope.launch {
            while (isActive) {
                val cameraFps = cameraFpsProvider().coerceAtLeast(1f)
                val decodeRate = decodeRateProvider().coerceIn(0f, 1f)
                val blur = blurProvider().coerceAtLeast(0f)

                val suggestion = mlBridge.suggestRotationHz(cameraFps, decodeRate, blur)
                    .getOrNull() ?: DEFAULT_SUGGESTION_HZ

                val clamped = faceRateMatcher.clamp(suggestion, faceCount, cameraFps)
                val writeResult = motorController.setTargetRotationHz(clamped.hz)

                _policyState.value = MotorPolicyState(
                    cameraFps = cameraFps,
                    decodeRate = decodeRate,
                    blurSigmaPx = blur,
                    rawSuggestionHz = suggestion,
                    commandedHz = clamped.hz,
                    clampReason = clamped.reason,
                    lastWriteSucceeded = writeResult is PrismResult.Success,
                )

                if (writeResult is PrismResult.Failure) {
                    logger.w(TAG, "Motor write failed: ${writeResult.error.code}")
                }

                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    data class MotorPolicyState(
        val cameraFps: Float = 0f,
        val decodeRate: Float = 0f,
        val blurSigmaPx: Float = 0f,
        val rawSuggestionHz: Float = 0f,
        val commandedHz: Float = 0f,
        val clampReason: FaceRateMatcher.ClampReason = FaceRateMatcher.ClampReason.NONE,
        val lastWriteSucceeded: Boolean = false,
    )

    companion object {
        private const val TAG = "MotorPolicyLoop"
        private const val INTERVAL_MS = 100L
        private const val DEFAULT_SUGGESTION_HZ = 0.25f
    }
}
