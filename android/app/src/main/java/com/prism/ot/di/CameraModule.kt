package com.prism.ot.di

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import com.prism.ot.capture.CameraController
import com.prism.ot.capture.CameraControllerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.resume

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CameraExecutor

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    @Binds @Singleton
    abstract fun bindCameraController(impl: CameraControllerImpl): CameraController

    companion object {

        @Provides @Singleton
        @CameraExecutor
        fun provideCameraExecutor(): ExecutorService =
            Executors.newFixedThreadPool(3) { r ->
                Thread(r, "prism-camera").apply { priority = Thread.NORM_PRIORITY + 1 }
            }

        /**
         * Resolves the process-wide CameraProvider lazily and suspends until
         * the underlying future completes. Callers should cache the result.
         */
        @Provides @Singleton
        suspend fun provideCameraProvider(
            @ApplicationContext context: Context,
        ): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching { future.get() }
                    .onSuccess { cont.resume(it) }
                    .onFailure { cont.resumeWith(Result.failure(it)) }
            }, context.mainExecutor)
            cont.invokeOnCancellation { future.cancel(true) }
        }
    }
}
