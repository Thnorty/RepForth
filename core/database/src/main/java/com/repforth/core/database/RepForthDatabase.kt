package com.repforth.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.repforth.core.database.dao.ExerciseDao
import com.repforth.core.database.entity.ExerciseEntity
import com.repforth.core.database.entity.ExerciseInstructionStepEntity
import com.repforth.core.database.entity.ExerciseSecondaryMuscleEntity
import com.repforth.core.database.entity.MovementExclusionEntity
import com.repforth.core.database.entity.ProfileEquipmentEntity
import com.repforth.core.database.entity.ProfilePreferredMuscleEntity
import com.repforth.core.database.entity.SessionExerciseEntity
import com.repforth.core.database.entity.SetRecordEntity
import com.repforth.core.database.entity.TemplateExerciseEntity
import com.repforth.core.database.entity.UserProfileEntity
import com.repforth.core.database.entity.WorkoutSessionEntity
import com.repforth.core.database.entity.WorkoutTemplateEntity

/**
 * The single Room database (§7).
 *
 * Two halves with different lifetimes. The catalog is read-only and is replaced
 * wholesale when the dataset pin moves. The user-data tables are the only copy
 * of a person's training history that exists anywhere — there is no server to
 * restore from.
 *
 * That is why **no user-data table has a foreign key to `exercise`**. A CASCADE
 * would delete history along with a retired exercise; a RESTRICT would make a
 * dataset update impossible. Catalog ids are stored as plain indexed columns and
 * a missing exercise is handled at display time.
 *
 * Still version 1: the schema has never been released, so adding these tables
 * needs no migration and inventing one would be fiction. It does change Room's
 * identity hash, so the packaged catalog must be rebuilt — `PackagedCatalogTest`
 * fails until it is. From the first public release onward §7 requires explicit
 * migrations and forbids destructive ones.
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
        // Catalog: read-only, replaced wholesale when the dataset pin moves.
        ExerciseEntity::class,
        ExerciseSecondaryMuscleEntity::class,
        ExerciseInstructionStepEntity::class,

        // User data: the only copy that exists anywhere.
        UserProfileEntity::class,
        ProfileEquipmentEntity::class,
        ProfilePreferredMuscleEntity::class,
        MovementExclusionEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetRecordEntity::class,
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
