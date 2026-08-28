package com.repforth.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.repforth.core.database.dao.ExerciseDao
import com.repforth.core.database.entity.ExerciseEntity
import com.repforth.core.database.entity.ExerciseInstructionStepEntity
import com.repforth.core.database.entity.ExerciseSecondaryMuscleEntity

/**
 * The single Room database (§7).
 *
 * Version 1 holds the exercise catalog only. The user-data tables — templates,
 * sessions, set records, profile, audit — arrive in Phase 1 alongside the code
 * that writes them; declaring them now would mean shipping a schema nothing can
 * fill and migrating it before it has ever held a row.
 *
 * There is deliberately no `fallbackToDestructiveMigration`. Room's default on a
 * missing migration is to throw, and that is the behaviour §7 asks for: losing a
 * user's only copy of their history is not an acceptable upgrade path.
 *
 * Schemas are exported to `core/database/schemas` and committed. Once a version
 * is released, its JSON is a fixed record and must not be edited.
 */
@Database(
    version = RepForthDatabase.VERSION,
    exportSchema = true,
    entities = [
        ExerciseEntity::class,
        ExerciseSecondaryMuscleEntity::class,
        ExerciseInstructionStepEntity::class,
    ],
)
abstract class RepForthDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    companion object {
        const val VERSION = 1

        /** Also the asset filename once the import task prepackages the catalog. */
        const val NAME = "repforth.db"
    }
}
