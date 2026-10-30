package com.prism.ot.capture

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.FrameData
import com.prism.ot.data.model.TransferMode
import com.prism.ot.di.CameraExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX implementation of [CameraController].
 *
 * Frame-rate strategy: CameraX's Feature Group API does not yet support
 * ImageAnalysis with `FPS_60`; constructing such a session throws.
 * The only reliable way to request a fixed frame rate for ImageAnalysis is
 * through Camera2 interop's `CONTROL_AE_TARGET_FPS_RANGE`. The controller
 * therefore queries the device's supported ranges and selects the highest
 * fixed range, falling back to the highest variable range only when no
 * fixed range exists.
 *
 * Rolling-shutter telemetry: a Camera2 capture callback records
 * `SENSOR_ROLLING_SHUTTER_SKEW` from every completed capture. The value is
 * exposed in [CameraSessionInfo] and consumed by [RollingShutterEstimator].
 */
@OptIn(ExperimentalCamera2Interop::class)
@Singleton
class CameraControllerImpl @Inject constructor(
    private val cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    @CameraExecutor private val cameraExecutor: ExecutorService,
    @ApplicationContext private val context: Context,
    private val logger: PrismLogger,
) : CameraController {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var previewView: PreviewView? = null

    private val _sessionInfo = MutableStateFlow<CameraSessionInfo?>(null)
    override val sessionInfo: StateFlow<CameraSessionInfo?> = _sessionInfo.asStateFlow()

    private val _actualFps = MutableStateFlow(0f)
    override val actualFps: StateFlow<Float> = _actualFps.asStateFlow()

    private val rollingShutterSkewNanos = AtomicLong(0L)

    private val bindingDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate

    override suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: FrameAnalyzer,
        mode: TransferMode,
    ): PrismResult<CameraSessionInfo> = runCatching {
        val provider = awaitProvider()
        provider.unbindAll()

        this.previewView = previewView
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val cameraInfo = provider.getCameraInfo(selector)
        val capabilities = queryCapabilities(cameraInfo)
        val fpsRange = chooseFpsRange(capabilities.supportedFpsRanges)

        val previewUseCase = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        val analysisBuilder = ImageAnalysis.Builder()
            .setResolutionSelector(ANALYSIS_RESOLUTION_SELECTOR)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageRotationEnabled(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            analysisBuilder.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        }

        applyCamera2Interop(analysisBuilder, fpsRange)

        val analysis = analysisBuilder.build()
        val fpsMonitor = CameraFpsMonitor()

        analysis.setAnalyzer(cameraExecutor) { proxy ->
            val frame = FrameData(
                imageProxy = proxy,
                timestampNanos = proxy.imageInfo.timestamp,
                rotationDegrees = proxy.imageInfo.rotationDegrees,
                width = proxy.width,
                height = proxy.height,
                estimatedFps = fpsMonitor.onFrame(proxy.imageInfo.timestamp),
            )
            try {
                // The analyzer executor is a dedicated background thread.
                // runBlocking here is intentional: it keeps the ImageProxy
                // alive until analysis completes and preserves the
                // latest-only backpressure contract.
                runBlocking { analyzer.analyze(frame) }
            } catch (t: Throwable) {
                logger.e(TAG, "Analyzer threw: ${t.message}", t)
            } finally {
                _actualFps.value = fpsMonitor.currentFps()
                proxy.close()
            }
        }

        val boundCamera = provider.bindToLifecycle(
            lifecycleOwner,
            selector,
            previewUseCase,
            analysis,
        )

        this.cameraProvider = provider
        this.camera = boundCamera
        this.preview = previewUseCase
        this.imageAnalysis = analysis

        val info = CameraSessionInfo(
            selectedFpsRange = fpsRange,
            supportedFpsRanges = capabilities.supportedFpsRanges,
            sensorOrientationDegrees = capabilities.sensorOrientationDegrees,
            rollingShutterSkewNanos = rollingShutterSkewNanos.get(),
            isRgbaOutput = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
        _sessionInfo.value = info
        logger.i(
            TAG,
            "Camera bound: fps=${fpsRange.first}..${fpsRange.upper}, " +
                "skew=${info.rollingShutterSkewNanos}ns, rgba=${info.isRgbaOutput}",
        )
        info
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { t ->
            logger.e(TAG, "Camera bind failed", t)
            PrismResult.Failure(
                PrismError.CameraInitializationFailed(t.message ?: t.javaClass.simpleName),
                t,
            )
        },
    )

    override fun unbind() {
        runCatching { imageAnalysis?.clearAnalyzer() }
        runCatching { cameraProvider?.unbindAll() }
        imageAnalysis = null
        preview = null
        camera = null
        previewView = null
        _sessionInfo.value = null
        _actualFps.value = 0f
    }

    override fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    override fun setZoom(ratio: Float) {
        val max = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(1f, max))
    }

    override suspend fun focusAt(normalizedX: Float, normalizedY: Float): PrismResult<Unit> =
        runCatching {
            val view = previewView ?: error("PreviewView not bound")
            val point = view.meteringPointFactory.createPoint(
                normalizedX.coerceIn(0f, 1f) * view.width,
                normalizedY.coerceIn(0f, 1f) * view.height,
            )
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
            ).build()
            camera?.cameraControl?.startFocusAndMetering(action)
            Unit
        }.fold(
            onSuccess = { PrismResult.Success(it) },
            onFailure = { t ->
                logger.w(TAG, "focusAt failed", t)
                PrismResult.Failure(PrismError.CameraInitializationFailed(t.message ?: "focus"), t)
            },
        )

    // ---- Internals ----

    private suspend fun awaitProvider(): ProcessCameraProvider =
        if (cameraProviderFuture.isDone) {
            cameraProviderFuture.get()
        } else {
            suspendCancellableCoroutine { cont ->
                cameraProviderFuture.addListener(
                    {
                        runCatching { cameraProviderFuture.get() }
                            .onSuccess { cont.resume(it) }
                            .onFailure { cont.resumeWithException(it) }
                    },
                    cameraExecutor,
                )
                cont.invokeOnCancellation { /* future cannot be cancelled safely */ }
            }
        }

    @Suppress("DEPRECATION")
    private fun applyCamera2Interop(
        builder: ImageAnalysis.Builder,
        fpsRange: IntRange,
    ) {
        val extender = Camera2Interop.Extender(builder)
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            android.util.Range(fpsRange.first, fpsRange.upper),
        )
        extender.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: CaptureResult,
            ) {
                result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)?.let {
                    rollingShutterSkewNanos.set(it)
                }
            }
        })
    }

    private fun queryCapabilities(cameraInfo: CameraInfo): CameraCapabilities {
        val camera2Info = Camera2CameraInfo.from(cameraInfo)
        val ranges = camera2Info
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { it.lower..it.upper }
            ?: listOf(30..30)
        val orientation = camera2Info
            .getCameraCharacteristic(CameraCharacteristics.SENSOR_ORIENTATION)
            ?: 0
        return CameraCapabilities(ranges, orientation)
    }

    /**
     * Prefer a fixed range at 60, then 30, then 24, then 15, then 10 fps.
     * If none exists, choose the range with the highest upper bound.
     */
    private fun chooseFpsRange(supported: List<IntRange>): IntRange {
        val preferred = listOf(60, 30, 24, 15, 10)
        for (target in preferred) {
            supported.firstOrNull { it.first == target && it.upper == target }
                ?.let { return it }
        }
        for (target in preferred) {
            supported.firstOrNull { target in it }?.let { return it }
        }
        return supported.maxByOrNull { it.upper } ?: 30..30
    }

    companion object {
        private const val TAG = "CameraController"

        private val ANALYSIS_RESOLUTION_SELECTOR: ResolutionSelector =
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()
    }
}
