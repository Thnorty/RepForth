package com.repforth.core.database.di

import android.content.Context
import androidx.room.Room
import com.repforth.core.database.RepForthDatabase
import com.repforth.core.database.dao.ExerciseDao
import com.repforth.core.database.dao.ProfileDao
import com.repforth.core.database.dao.SessionDao
import com.repforth.core.database.dao.TemplateDao
import com.repforth.core.database.dao.WeekDao
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

    /** Produced by `tools/import-dataset.py`; see `dataset-version.toml`. */
    private const val ASSET = "repforth.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RepForthDatabase =
        Room.databaseBuilder(context, RepForthDatabase::class.java, RepForthDatabase.NAME)
            // The catalog ships prepackaged (§6, step 6), built by
            // tools/import-dataset.py from the pinned commit. A first launch
            // copies a file instead of parsing 1,324 records on the main thread
            // (§16), and the asset carries Room's identity hash, so a database
            // built from different entities is refused rather than half-read.
            .createFromAsset(ASSET)
            .addMigrations(RepForthDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideExerciseDao(database: RepForthDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideProfileDao(database: RepForthDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideTemplateDao(database: RepForthDatabase): TemplateDao = database.templateDao()

    @Provides
    fun provideWeekDao(database: RepForthDatabase): WeekDao = database.weekDao()

    @Provides
    fun provideSessionDao(database: RepForthDatabase): SessionDao = database.sessionDao()
}
