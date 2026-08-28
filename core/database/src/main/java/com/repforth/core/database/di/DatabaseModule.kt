package com.repforth.core.database.di

import android.content.Context
import androidx.room.Room
import com.repforth.core.database.RepForthDatabase
import com.repforth.core.database.dao.ExerciseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The database is a singleton because Room's connection pool is: building two
 * instances over one file is a documented way to get locking bugs.
 *
 * DAOs are provided individually so consumers depend on the one interface they
 * use rather than on the whole database — a feature module asking for
 * [RepForthDatabase] can reach every table, which is exactly the coupling the
 * module boundaries exist to prevent.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RepForthDatabase =
        Room.databaseBuilder(context, RepForthDatabase::class.java, RepForthDatabase.NAME)
            // The catalog will ship prepackaged (§6, step 6): once the import
            // task emits the asset this becomes `createFromAsset(NAME)`, so a
            // first launch does not parse 1,324 records on the main thread.
            // Until the asset exists, an empty database is the honest state.
            .build()

    @Provides
    fun provideExerciseDao(database: RepForthDatabase): ExerciseDao = database.exerciseDao()
}
