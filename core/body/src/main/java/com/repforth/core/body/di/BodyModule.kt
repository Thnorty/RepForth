package com.repforth.core.body.di

import com.repforth.core.body.BodyRepository
import com.repforth.core.body.RoomBodyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface BodyModule {

    /** A singleton, because it remembers whether built-ins are seeded and photos swept. */
    @Binds
    @Singleton
    fun bindBodyRepository(impl: RoomBodyRepository): BodyRepository
}
