package com.repforth.core.exercisedata.di

import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.exercisedata.RoomExerciseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the interface to the Room implementation, so consumers inject
 * [ExerciseRepository] and never learn which storage is behind it.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface ExerciseDataModule {

    @Binds
    @Singleton
    fun bindExerciseRepository(impl: RoomExerciseRepository): ExerciseRepository
}
