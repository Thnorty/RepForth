package com.repforth.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.repforth.core.database.dao.ExerciseDao
import com.repforth.core.database.dao.ProfileDao
import com.repforth.core.database.dao.SessionDao
import com.repforth.core.database.dao.TemplateDao
import com.repforth.core.database.dao.WeekDao
import com.repforth.core.database.entity.ExerciseEntity
import com.repforth.core.database.entity.ExerciseInstructionStepEntity
import com.repforth.core.database.entity.ExerciseSecondaryMuscleEntity
import com.repforth.core.database.entity.MovementExclusionEntity
import com.repforth.core.database.entity.ProfileEquipmentEntity
import com.repforth.core.database.entity.ProfilePreferredMuscleEntity
import com.repforth.core.database.entity.SessionExerciseEntity
import com.repforth.core.database.entity.SetRecordEntity
import com.repforth.core.database.entity.TemplateExerciseEntity
import com.repforth.core.database.entity.TrainingWeekEntity
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
 * Explicit migrations are required from version 1 onward (§7, §18). Destructive
 * migrations are forbidden.
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
        TrainingWeekEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetRecordEntity::class,
    ],
)
abstract class RepForthDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun profileDao(): ProfileDao

    abstract fun templateDao(): TemplateDao

    abstract fun weekDao(): WeekDao

    abstract fun sessionDao(): SessionDao

    companion object {
        const val VERSION = 2

        /** Also the asset filename once the import task prepackages the catalog. */
        const val NAME = "repforth.db"

        /**
         * Migration from v1 to v2: adds `training_week` and links `workout_template`
         * to it via `week_id`, `week_position`, and `day_of_week` (§3.1, §3.4).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `training_week` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `notes` TEXT,
                        `source` TEXT NOT NULL,
                        `active` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE `workout_template` ADD COLUMN `week_id` TEXT REFERENCES `training_week`(`id`) ON DELETE CASCADE")
                db.execSQL("ALTER TABLE `workout_template` ADD COLUMN `week_position` INTEGER")
                db.execSQL("ALTER TABLE `workout_template` ADD COLUMN `day_of_week` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_template_week_id` ON `workout_template` (`week_id`)")
            }
        }
    }
}
