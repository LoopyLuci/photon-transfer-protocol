package com.prism.ot.di

import com.prism.ot.io.FileHandler
import com.prism.ot.io.FileHandlerImpl
import com.prism.ot.io.ReceivedFileStore
import com.prism.ot.io.ReceivedFileStoreImpl
import com.prism.ot.io.Sha256Verifier
import com.prism.ot.io.Sha256VerifierImpl
import com.prism.ot.transfer.TransferSession
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IoModule {

    @Binds @Singleton
    abstract fun bindFileHandler(impl: FileHandlerImpl): FileHandler

    @Binds @Singleton
    abstract fun bindSha256Verifier(impl: Sha256VerifierImpl): Sha256Verifier

    @Binds @Singleton
    abstract fun bindReceivedFileStore(impl: ReceivedFileStoreImpl): ReceivedFileStore
}
