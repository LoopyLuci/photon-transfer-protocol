package com.prism.ot.di

import com.prism.ot.capture.CameraController
import com.prism.ot.capture.CameraSessionInfo
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.TransferMode
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

/**
 * Replaces the real CameraX controller with a fake in instrumented tests.
 *
 * `@TestInstallIn` is preferred over `@UninstallModules` because it is
 * declarative and applies to every test in the module, which removes the
 * per-test teardown burden. The replacement module is compiled
 * only into `androidTest`, so production builds are unaffected.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CameraModule::class],
)
object FakeCameraModule {

    @Provides @Singleton
    fun provideFakeCameraController(): CameraController = FakeCameraController()
}

class FakeCameraController : CameraController {
    private val _sessionInfo = MutableStateFlow<CameraSessionInfo?>(null)
    private val _fps = MutableStateFlow(30f)

    override val sessionInfo = _sessionInfo
    override val actualFps = _fps

    override suspend fun bind(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView,
        analyzer: com.prism.ot.capture.FrameAnalyzer,
        mode: TransferMode,
    ): PrismResult<CameraSessionInfo> {
        val info = CameraSessionInfo(
            selectedFpsRange = 30..30,
            supportedFpsRanges = listOf(30..30),
            sensorOrientationDegrees = 90,
            rollingShutterSkewNanos = 16_000_000L,
            isRgbaOutput = true,
        )
        _sessionInfo.value = info
        return PrismResult.Success(info)
    }

    override fun unbind() { _sessionInfo.value = null }
    override fun setTorch(enabled: Boolean) {}
    override fun setZoom(ratio: Float) {}
    override suspend fun focusAt(normalizedX: Float, normalizedY: Float) =
        PrismResult.Success(Unit)
}
