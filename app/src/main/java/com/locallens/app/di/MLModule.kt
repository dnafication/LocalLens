package com.locallens.app.di

import android.content.Context
import com.locallens.app.data.ml.FaceDetector
import com.locallens.app.data.ml.FaceEmbedder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MLModule {

    @Provides
    @Singleton
    fun provideFaceDetector(): FaceDetector = FaceDetector()

    @Provides
    @Singleton
    fun provideFaceEmbedder(@ApplicationContext context: Context): FaceEmbedder = FaceEmbedder(context)
}
