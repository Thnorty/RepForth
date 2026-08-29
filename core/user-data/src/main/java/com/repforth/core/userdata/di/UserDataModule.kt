package com.repforth.core.userdata.di

import com.repforth.core.common.time.SystemTimeSource
import com.repforth.core.common.time.TimeSource
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.RoomSessionRepository
import com.repforth.core.userdata.RoomTemplateRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.RoomProfileRepository
import dagger.Binds
import dagger.Provides
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
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

    @Binds
    @Singleton
    fun bindSessionRepository(impl: RoomSessionRepository): SessionRepository

    /**
     * Bound here rather than in core:common so that module stays free of Hilt
     * components — it only borrows the annotations.
     */
    @Binds
    @Singleton
    fun bindTimeSource(impl: SystemTimeSource): TimeSource

    companion object {
        /**
         * The zone "this week" is measured in.
         *
         * Injected for the same reason the clock is: a streak that changes
         * depending on which machine computed it is not testable, and a
         * statistic that silently follows the host's default is the kind of
         * thing that only disagrees with itself while travelling.
         */
        @Provides
        @Singleton
        fun provideZoneId(): ZoneId = ZoneId.systemDefault()
    }
}
