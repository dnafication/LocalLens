package com.locallens.app.di

import android.content.Context
import com.locallens.app.data.media.MediaStoreScanner
import com.locallens.app.data.media.VideoFrameExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideMediaStoreScanner(@ApplicationContext context: Context): MediaStoreScanner {
        return MediaStoreScanner(context)
    }

    @Provides
    @Singleton
    fun provideVideoFrameExtractor(): VideoFrameExtractor = VideoFrameExtractor()
}
