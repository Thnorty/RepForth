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
        const val VERSION = 4

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

        /**
         * Migration from v2 to v3: `workout_session` records where the user is.
         *
         * Position used to be recomputed on read from the set records, and that
         * derivation was wrong in two states the engine really has — see
         * [com.repforth.core.database.entity.WorkoutSessionEntity]. A paused
         * rest had nowhere to keep its remainder at all.
         *
         * The backfill reproduces the old derivation rather than leaving the
         * defaults in place. Only an unfinished session reads these columns, and
         * there is at most one — but that one belongs to somebody in the middle
         * of a workout when the update lands, and without the backfill they come
         * back to the first set of the first exercise. The old answer is the
         * best answer available for a row written before the fix, and it is the
         * answer they were already getting.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_session` ADD COLUMN `rest_remaining_ms` INTEGER")
                db.execSQL(
                    "ALTER TABLE `workout_session` ADD COLUMN `current_exercise_index` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `workout_session` ADD COLUMN `current_set_index` INTEGER NOT NULL DEFAULT 0",
                )

                // The first exercise still owed sets, by position — which is
                // also its index, because a template's positions are contiguous
                // from zero. The second COALESCE arm is the old fallback for a
                // session with no exercise left owing anything.
                db.execSQL(
                    """
                    UPDATE `workout_session` SET `current_exercise_index` = COALESCE((
                        SELECT MIN(e.`position`) FROM `session_exercise` e
                        WHERE e.`session_id` = `workout_session`.`id`
                          AND (
                            SELECT COUNT(*) FROM `set_record` r
                            WHERE r.`session_exercise_id` = e.`id`
                          ) < e.`target_sets`
                    ), (
                        SELECT MAX(e.`position`) FROM `session_exercise` e
                        WHERE e.`session_id` = `workout_session`.`id`
                    ), 0)
                    """.trimIndent(),
                )

                // And the count of sets already recorded against that exercise.
                db.execSQL(
                    """
                    UPDATE `workout_session` SET `current_set_index` = COALESCE((
                        SELECT COUNT(*) FROM `set_record` r
                        WHERE r.`session_exercise_id` = (
                            SELECT e.`id` FROM `session_exercise` e
                            WHERE e.`session_id` = `workout_session`.`id`
                              AND e.`position` = `workout_session`.`current_exercise_index`
                        )
                    ), 0)
                    """.trimIndent(),
                )
            }
        }

        /**
         * Migration from v3 to v4: a timed set gets its own clock.
         *
         * §3 asks for timed intervals and, until now, the app displayed a target
         * duration and recorded that same number whenever the user tapped "Log
         * set" — so a plank abandoned at forty seconds was filed as sixty. The
         * clock is what completes one now, and it needs somewhere to survive a
         * process death, exactly as rest does.
         *
         * Both columns are nullable with no backfill, and that is correct rather
         * than lazy: null means "no timed set is counting", which is true of
         * every row written before this existed. Nobody had a running timed set,
         * because there were none.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_session` ADD COLUMN `set_deadline_at` INTEGER")
                db.execSQL("ALTER TABLE `workout_session` ADD COLUMN `set_remaining_ms` INTEGER")
            }
        }
    }
}
