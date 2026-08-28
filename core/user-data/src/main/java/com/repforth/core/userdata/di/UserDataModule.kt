package com.repforth.core.userdata.di

import com.repforth.core.common.time.SystemTimeSource
import com.repforth.core.common.time.TimeSource
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.RoomTemplateRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.RoomProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface UserDataModule {

    @Binds
    @Singleton
    fun bindProfileRepository(impl: RoomProfileRepository): ProfileRepository

    @Binds
    @Singleton
    fun bindTemplateRepository(impl: RoomTemplateRepository): TemplateRepository

    /**
     * Bound here rather than in core:common so that module stays free of Hilt
     * components — it only borrows the annotations.
     */
    @Binds
    @Singleton
    fun bindTimeSource(impl: SystemTimeSource): TimeSource
}
