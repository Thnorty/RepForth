package com.repforth.core.transfer.di

import com.repforth.core.transfer.DataTransfer
import com.repforth.core.transfer.DefaultDataTransfer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The one door to export, import and the two deletes. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TransferModule {

    @Binds
    @Singleton
    abstract fun bindDataTransfer(impl: DefaultDataTransfer): DataTransfer
}
