package com.locallens.app.di

import com.locallens.app.data.repository.FaceRepository
import com.locallens.app.data.repository.FaceRepositoryImpl
import com.locallens.app.data.repository.MediaRepository
import com.locallens.app.data.repository.MediaRepositoryImpl
import com.locallens.app.data.repository.PersonRepository
import com.locallens.app.data.repository.PersonRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindPersonRepository(impl: PersonRepositoryImpl): PersonRepository

    @Binds
    @Singleton
    abstract fun bindFaceRepository(impl: FaceRepositoryImpl): FaceRepository
}
