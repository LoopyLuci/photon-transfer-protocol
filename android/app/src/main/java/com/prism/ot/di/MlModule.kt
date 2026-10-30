package com.prism.ot.di

import android.content.Context
import com.prism.ot.ml.MlBridge
import com.prism.ot.ml.MlBridgeImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the ML façade. The implementation is split across Batch 3:
 * [MlBridge] is the pure interface, [MlBridgeImpl] is the JNI + NNAPI
 * orchestrator. Individual model wrappers are injected into the bridge
 * via constructor injection.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {

    @Binds @Singleton
    abstract fun bindMlBridge(impl: MlBridgeImpl): MlBridge

    companion object {
        @Provides @Singleton
        fun provideModelAssetPath(@ApplicationContext context: Context): String =
            "models"
    }
}
