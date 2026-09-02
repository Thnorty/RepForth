package com.repforth.feature.exercises

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.exercisedata.CatalogFilter
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
import com.repforth.core.testing.screenshotPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The catalog list, whose rows hold the longest text in the app.
 *
 * Exercise names average 26 characters and run past 40, they are English in
 * both languages because upstream ships one name per record, and they sit
 * beside a thumbnail and two metadata labels that *are* translated. That
 * combination is the whole risk on this screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class ExercisesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalog_english() = capture("catalog-en", POPULATED)

    @Test
    fun catalog_turkish() = capture("catalog-tr", POPULATED, locale = TURKISH)

    @Test
    fun catalog_large_text() = capture("catalog-en-2x", POPULATED, fontScale = 2f)

    @Test
    fun catalog_turkish_large_text() =
        capture("catalog-tr-2x", POPULATED, locale = TURKISH, fontScale = 2f)

    /** "Nothing matches" and "still loading" look alike, and only one is an answer. */
    @Test
    fun catalog_no_matches() = capture(
        "catalog-empty-en",
        ExercisesUiState(filter = CatalogFilter(query = "zzz"), loading = false),
    )

    private fun capture(
        name: String,
        state: ExercisesUiState,
        locale: String? = null,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setQualifiers("+${locale ?: ENGLISH}")
        RuntimeEnvironment.setFontScale(fontScale)

        compose.setContent {
            RepForthPreviewHost {
                ExercisesScreen(
                    state = state,
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

        compose.onRoot().captureRoboImage(screenshotPath(name))
    }

    private companion object {
        val POPULATED = ExercisesUiState(
            results = listOf(
                summary("0025", "barbell decline wide-grip press", Muscle.PECTORALS, Equipment.BARBELL),
                summary("0043", "dumbbell incline hammer curl", Muscle.BICEPS, Equipment.DUMBBELL),
                summary("1671", "assisted seated pectoralis major stretch with stability ball", Muscle.PECTORALS, Equipment.BODY_WEIGHT),
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
