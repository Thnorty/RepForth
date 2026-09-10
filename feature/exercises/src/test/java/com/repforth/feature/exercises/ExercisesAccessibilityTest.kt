package com.repforth.feature.exercises

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.MediaRef
import com.repforth.core.model.Muscle
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.assertScreenIsAccessible
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What TalkBack would find in the catalog.
 *
 * The rows carry a thumbnail beside two translated labels, and the thumbnail is
 * the kind of element that ends up with no description and no reason for it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class ExercisesAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalog_english() = check(ENGLISH)

    @Test
    fun catalog_turkish() = check(TURKISH)

    private fun check(locale: String) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)

        compose.setContent {
            RepForthPreviewHost {
                ExercisesScreen(
                    state = POPULATED,
                    onQueryChange = {},
                    onBodyPartSelected = {},
                    onEquipmentSelected = {},
                    onMuscleToggled = {},
                    onRegionToggled = {},
                    onClearFilters = {},
                    onSelectExercise = {},
                    onDismissDetail = {},
                )
            }
        }

        compose.assertScreenIsAccessible("Catalog ($locale)")
    }

    private companion object {
        val POPULATED = ExercisesUiState(
            results = listOf(
                summary("0025", "barbell decline wide-grip press", Muscle.PECTORALS, Equipment.BARBELL),
                summary("0043", "dumbbell incline hammer curl", Muscle.BICEPS, Equipment.DUMBBELL),
                summary("0652", "pull up", Muscle.LATS, Equipment.BODY_WEIGHT),
            ),
            loading = false,
        )

        fun summary(id: String, name: String, target: Muscle, equipment: Equipment) =
            ExerciseSummary(
                id = ExerciseId(id),
                name = name,
                bodyPart = BodyPart.CHEST,
                target = target,
                equipment = equipment,
                thumbnail = MediaRef.Unavailable,
            )
    }
}
