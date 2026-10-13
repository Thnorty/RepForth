package com.repforth.feature.body

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.repforth.core.designsystem.theme.LocalUnitSystem
import com.repforth.core.designsystem.theme.formatDecimal
import com.repforth.core.designsystem.theme.fromCentimetres
import com.repforth.core.designsystem.theme.fromKilograms
import com.repforth.core.designsystem.theme.lengthSymbol
import com.repforth.core.designsystem.theme.readDecimal
import com.repforth.core.designsystem.theme.symbol
import com.repforth.core.designsystem.theme.toCentimetres
import com.repforth.core.designsystem.theme.toKilograms
import com.repforth.core.model.BuiltInMeasurement
import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.MeasurementType
import com.repforth.core.model.Pose
import com.repforth.core.model.UnitSystem
import java.util.Locale

/**
 * A typed value, in the unit the user reads, back in the unit it is stored in.
 *
 * Mass and length convert through the unit setting (§7); percent and a custom
 * number are stored as typed. Null for blank or unreadable text — range is
 * `CheckInRules`' concern, so a negative number reads as one.
 */
fun readMeasurement(text: String, kind: MeasurementKind, units: UnitSystem): Double? {
    val value = readDecimal(text) ?: return null
    return when (kind) {
        MeasurementKind.MASS -> units.toKilograms(value)
        MeasurementKind.LENGTH -> units.toCentimetres(value)
        MeasurementKind.PERCENT, MeasurementKind.PLAIN -> value
    }
}

/** A stored value as the number the user reads, without its unit. */
@Composable
fun formatMeasurementNumber(value: Double, kind: MeasurementKind): String =
    measurementNumber(value, kind, LocalUnitSystem.current, LocalConfiguration.current.locales[0])

/** [formatMeasurementNumber] for a caller outside composition, such as a chart's axis. */
fun measurementNumber(value: Double, kind: MeasurementKind, units: UnitSystem, locale: Locale): String {
    val shown = when (kind) {
        MeasurementKind.MASS -> units.fromKilograms(value)
        MeasurementKind.LENGTH -> units.fromCentimetres(value)
        MeasurementKind.PERCENT, MeasurementKind.PLAIN -> value
    }
    return formatDecimal(shown, locale)
}

/** The unit beside a figure: kg or lb, cm or in, %, or the user's own label. */
@Composable
fun MeasurementType.unit(): String {
    val units = LocalUnitSystem.current
    return when (kind) {
        MeasurementKind.MASS -> units.symbol
        MeasurementKind.LENGTH -> units.lengthSymbol
        MeasurementKind.PERCENT -> "%"
        MeasurementKind.PLAIN -> unitLabel.orEmpty()
    }
}

/**
 * A stored value with its unit, as one string: "82.4 kg", "20%" — and "%20" in
 * Turkish, which writes the sign first.
 */
@Composable
fun formatMeasurement(value: Double, type: MeasurementType): String {
    val number = formatMeasurementNumber(value, type.kind)
    return when {
        type.kind == MeasurementKind.PERCENT -> stringResource(R.string.body_value_percent, number)
        type.unit().isEmpty() -> number
        else -> stringResource(R.string.body_value_with_unit, number, type.unit())
    }
}

/** What the type is called: its resource for a built-in, its own name for a custom one. */
@Composable
fun MeasurementType.displayName(): String =
    builtIn?.let { stringResource(it.nameRes) } ?: customName.orEmpty()

@get:StringRes
internal val BuiltInMeasurement.nameRes: Int
    get() = when (this) {
        BuiltInMeasurement.WEIGHT -> R.string.body_type_weight
        BuiltInMeasurement.BODY_FAT -> R.string.body_type_body_fat
        BuiltInMeasurement.NECK -> R.string.body_type_neck
        BuiltInMeasurement.SHOULDERS -> R.string.body_type_shoulders
        BuiltInMeasurement.CHEST -> R.string.body_type_chest
        BuiltInMeasurement.WAIST -> R.string.body_type_waist
        BuiltInMeasurement.HIPS -> R.string.body_type_hips
        BuiltInMeasurement.ARM_LEFT -> R.string.body_type_arm_left
        BuiltInMeasurement.ARM_RIGHT -> R.string.body_type_arm_right
        BuiltInMeasurement.FOREARM_LEFT -> R.string.body_type_forearm_left
        BuiltInMeasurement.FOREARM_RIGHT -> R.string.body_type_forearm_right
        BuiltInMeasurement.THIGH_LEFT -> R.string.body_type_thigh_left
        BuiltInMeasurement.THIGH_RIGHT -> R.string.body_type_thigh_right
        BuiltInMeasurement.CALF_LEFT -> R.string.body_type_calf_left
        BuiltInMeasurement.CALF_RIGHT -> R.string.body_type_calf_right
    }

@get:StringRes
internal val Pose.labelRes: Int
    get() = when (this) {
        Pose.FRONT -> R.string.body_pose_front
        Pose.SIDE -> R.string.body_pose_side
        Pose.BACK -> R.string.body_pose_back
    }
