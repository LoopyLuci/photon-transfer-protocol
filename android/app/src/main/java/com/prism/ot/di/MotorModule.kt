package com.prism.ot.di

import android.content.Context
import com.prism.ot.motor.MotorController
import com.prism.ot.motor.MotorControllerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MotorModule {

    @Binds @Singleton
    abstract fun bindMotorController(impl: MotorControllerImpl): MotorController

    companion object {
        @Provides @Singleton
        fun provideBleContext(@ApplicationContext context: Context): Context = context
    }
}
