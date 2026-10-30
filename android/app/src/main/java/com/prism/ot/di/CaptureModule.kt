package com.prism.ot.di

import com.prism.ot.capture.FrameAnalyzer
import com.prism.ot.capture.PrismFrameAnalyzer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the frame analyzer. The controller, converter, and rolling-shutter
 * estimator are all `@Inject`-constructed singletons and need no explicit
 * provider.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {

    @Binds @Singleton
    abstract fun bindFrameAnalyzer(impl: PrismFrameAnalyzer): FrameAnalyzer
}
