package com.prism.ot.di

import com.prism.ot.codec.CodecBridge
import com.prism.ot.codec.CodecBridgeImpl
import com.prism.ot.codec.PrismCodec
import com.prism.ot.codec.PrismCodecImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the codec façade. [CodecBridge] is the pure JNI wrapper around
 * the Rust core; [PrismCodec] is the Kotlin-level encoder/decoder that
 * orchestrates alphabet, LDPC, and fountain layers on top of the bridge.
 * Both are defined in Batch 4.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CodecModule {

    @Binds @Singleton
    abstract fun bindCodecBridge(impl: CodecBridgeImpl): CodecBridge

    @Binds @Singleton
    abstract fun bindPrismCodec(impl: PrismCodecImpl): PrismCodec
}
