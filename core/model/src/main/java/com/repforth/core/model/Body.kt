package com.repforth.core.model

import java.time.LocalDate

/**
 * What a measurement is a quantity of, which decides how it is stored and shown.
 *
 * Stored canonically — kilograms, centimetres, percent — and converted only for
 * display (§7), so switching units never rewrites anything. [PLAIN] is a number
 * with the user's own unit label, and is never converted.
 */
enum class MeasurementKind { MASS, LENGTH, PERCENT, PLAIN }

/**
 * The measurements every install offers, in the order they are listed.
 *
 * [id] is stored in the database and written to exports, so it is part of the
 * file format: renaming one orphans every entry already recorded under it.
 */
enum class BuiltInMeasurement(
    val id: String,
    val kind: MeasurementKind = MeasurementKind.LENGTH,
    val trackedByDefault: Boolean = false,
) {
    WEIGHT("weight", MeasurementKind.MASS, trackedByDefault = true),
    BODY_FAT("body_fat", MeasurementKind.PERCENT, trackedByDefault = true),
    NECK("neck"),
    SHOULDERS("shoulders"),
    CHEST("chest"),
    WAIST("waist", trackedByDefault = true),
    HIPS("hips"),
    ARM_LEFT("arm_left"),
    ARM_RIGHT("arm_right"),
    FOREARM_LEFT("forearm_left"),
    FOREARM_RIGHT("forearm_right"),
    THIGH_LEFT("thigh_left"),
    THIGH_RIGHT("thigh_right"),
    CALF_LEFT("calf_left"),
    CALF_RIGHT("calf_right"),
    ;

    companion object {
        fun byId(id: String): BuiltInMeasurement? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Something the user can measure.
 *
 * A built-in has [builtIn] set and takes its name from string resources; a
 * custom one has [customName]. [unitLabel] is only for [MeasurementKind.PLAIN].
 * Turning [tracked] off hides the type; its history stays.
 */
data class MeasurementType(
    val id: String,
    val builtIn: BuiltInMeasurement?,
    val customName: String?,
    val kind: MeasurementKind,
    val unitLabel: String?,
    val tracked: Boolean,
    val sortOrder: Int,
)

/** The three poses a check-in can photograph. */
enum class Pose { FRONT, SIDE, BACK }

/**
 * One day's record of the body.
 *
 * [measurements] is keyed by [MeasurementType.id] and holds canonical units.
 * [photos] holds file names inside the app's private photo folder.
 */
data class CheckIn(
    val id: String,
    val date: LocalDate,
    val measurements: Map<String, Double>,
    val photos: Map<Pose, String>,
)

enum class CheckInProblem { EMPTY, FUTURE_DATE, NOT_POSITIVE, PERCENT_OVER_100, UNKNOWN_TYPE, DATE_TAKEN }

/**
 * What a check-in must hold before it is stored.
 *
 * Pure, so the repository, the import and the screen agree by calling the same
 * function. One day per check-in is the repository's to enforce, since it needs
 * the stored data: this never returns [CheckInProblem.DATE_TAKEN].
 */
object CheckInRules {

    fun check(checkIn: CheckIn, today: LocalDate, types: Map<String, MeasurementType>): CheckInProblem? {
        if (checkIn.measurements.isEmpty() && checkIn.photos.isEmpty()) return CheckInProblem.EMPTY
        if (checkIn.date.isAfter(today)) return CheckInProblem.FUTURE_DATE
        checkIn.measurements.forEach { (typeId, value) ->
            val type = types[typeId] ?: return CheckInProblem.UNKNOWN_TYPE
            if (!value.isFinite() || value <= 0.0) return CheckInProblem.NOT_POSITIVE
            if (type.kind == MeasurementKind.PERCENT && value > 100.0) return CheckInProblem.PERCENT_OVER_100
        }
        return null
    }
}
