package com.prism.ot.di

import com.prism.ot.session.AutoModeDetector
import com.prism.ot.session.SessionFactory
import com.prism.ot.session.SessionResultDeliverer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Session components are singletons only where they hold no per‑screen
 * state. The sessions themselves are constructed by [SessionFactory] and
 * are not part of the Hilt graph.
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides @Singleton
    fun provideAutoModeDetector(
        mlBridge: com.prism.ot.ml.MlBridge,
        logger: com.prism.ot.core.PrismLogger,
    ): AutoModeDetector = AutoModeDetector(mlBridge, logger)
}
