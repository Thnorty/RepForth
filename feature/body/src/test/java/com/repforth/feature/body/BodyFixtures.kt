package com.repforth.feature.body

import com.repforth.core.model.BuiltInMeasurement
import com.repforth.core.model.CheckIn
import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.MeasurementType
import com.repforth.core.model.Pose
import java.time.LocalDate

/** Fixed body data for the screen tests, so a golden never moves with the clock. */
internal object BodyFixtures {

    val TODAY: LocalDate = LocalDate.of(2026, 10, 13)

    val BUILT_INS = BuiltInMeasurement.entries.mapIndexed { index, builtIn ->
        MeasurementType(builtIn.id, builtIn, null, builtIn.kind, null, builtIn.trackedByDefault, index)
    }

    val SKINFOLD = MeasurementType("custom-1", null, "Abdominal skinfold", MeasurementKind.PLAIN, "mm", true, 15)

    /** Weight, body fat and waist tracked, plus a custom type and the left arm. */
    val TYPES = BUILT_INS.map { if (it.id == "arm_left") it.copy(tracked = true) else it } + SKINFOLD

    val CHECK_INS = listOf(
        CheckIn(
            "c1", TODAY.minusDays(28),
            mapOf("weight" to 84.2, "waist" to 91.0, "body_fat" to 21.5, "arm_left" to 36.0),
            mapOf(Pose.FRONT to "c1-front.jpg", Pose.SIDE to "c1-side.jpg"),
        ),
        CheckIn("c2", TODAY.minusDays(14), mapOf("weight" to 83.1, "waist" to 89.5, SKINFOLD.id to 18.0), emptyMap()),
        CheckIn(
            "c3", TODAY,
            mapOf("weight" to 82.4, "waist" to 88.0, "body_fat" to 20.0, "arm_left" to 36.5, SKINFOLD.id to 18.0),
            mapOf(Pose.FRONT to "c3-front.jpg"),
        ),
    )
}
