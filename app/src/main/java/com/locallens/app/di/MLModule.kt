package com.locallens.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object MLModule {
    // FaceDetectorWrapper and FaceEmbedder are provided via @Inject constructor
    // ModelAssetManager is provided via @Inject constructor
    // This module exists for future ML-related bindings (e.g., GPU delegate configuration)
}
