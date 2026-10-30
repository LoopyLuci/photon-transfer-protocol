package com.prism.ot.di

import com.prism.ot.ml.wrapper.CalibratorWrapper
import com.prism.ot.ml.wrapper.ChannelTransformerWrapper
import com.prism.ot.ml.wrapper.ClassifierWrapper
import com.prism.ot.ml.wrapper.DeblurrerWrapper
import com.prism.ot.ml.wrapper.DecodabilityWrapper
import com.prism.ot.ml.wrapper.DefectInspectorWrapper
import com.prism.ot.ml.wrapper.FaceTrackerWrapper
import com.prism.ot.ml.wrapper.FountainPolicyWrapper
import com.prism.ot.ml.wrapper.LocaliserWrapper
import com.prism.ot.ml.wrapper.MotorPolicyWrapper
import com.prism.ot.ml.wrapper.NeuralLdpcWrapper
import com.prism.ot.ml.wrapper.SuperResolutionWrapper
import com.prism.ot.ml.wrapper.SyntheticChannelWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MlWrappersModule {

    // Hilt can construct these directly via @Inject constructors. The
    // explicit @Provides methods below exist so integration tests can
    // substitute mock wrappers without changing production code.

    @Provides @Singleton
    fun provideDecodabilityWrapper(w: DecodabilityWrapper): DecodabilityWrapper = w

    @Provides @Singleton
    fun provideLocaliserWrapper(w: LocaliserWrapper): LocaliserWrapper = w

    @Provides @Singleton
    fun provideSuperResolutionWrapper(w: SuperResolutionWrapper): SuperResolutionWrapper = w

    @Provides @Singleton
    fun provideCalibratorWrapper(w: CalibratorWrapper): CalibratorWrapper = w

    @Provides @Singleton
    fun provideClassifierWrapper(w: ClassifierWrapper): ClassifierWrapper = w

    @Provides @Singleton
    fun provideNeuralLdpcWrapper(w: NeuralLdpcWrapper): NeuralLdpcWrapper = w

    @Provides @Singleton
    fun provideFountainPolicyWrapper(w: FountainPolicyWrapper): FountainPolicyWrapper = w

    @Provides @Singleton
    fun provideFaceTrackerWrapper(w: FaceTrackerWrapper): FaceTrackerWrapper = w

    @Provides @Singleton
    fun provideDeblurrerWrapper(w: DeblurrerWrapper): DeblurrerWrapper = w

    @Provides @Singleton
    fun provideMotorPolicyWrapper(w: MotorPolicyWrapper): MotorPolicyWrapper = w

    @Provides @Singleton
    fun provideChannelTransformerWrapper(w: ChannelTransformerWrapper): ChannelTransformerWrapper = w

    @Provides @Singleton
    fun provideDefectInspectorWrapper(w: DefectInspectorWrapper): DefectInspectorWrapper = w

    @Provides @Singleton
    fun provideSyntheticChannelWrapper(w: SyntheticChannelWrapper): SyntheticChannelWrapper = w
}
