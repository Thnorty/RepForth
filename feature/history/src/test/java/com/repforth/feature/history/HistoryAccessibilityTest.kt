package com.repforth.feature.history

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.assertScreenIsAccessible
import com.repforth.core.workout.ProgressSummary
import com.repforth.core.workout.WorkoutSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What TalkBack would find on Progress. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class HistoryAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun progress_english() = check(ENGLISH)

    @Test
    fun progress_turkish() = check(TURKISH)

    private fun check(locale: String) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent { RepForthPreviewHost { HistoryScreen(state = POPULATED) } }
        compose.assertScreenIsAccessible("Progress ($locale)")
    }

    private companion object {
        const val JANUARY_2026 = 1_767_225_600_000L

        val POPULATED = HistoryUiState(
            progress = ProgressSummary(
                workouts = 42,
                workoutsThisWeek = 3,
                daysThisWeek = 3,
                streakWeeks = 6,
                totalVolumeKg = 128_450.0,
                totalSets = 1_260,
                topMuscles = listOf("Pectorals", "Latissimus dorsi", "Quadriceps"),
            ),
            workouts = List(4) { index ->
                WorkoutSummary(
                    sessionId = "s$index",
                    templateId = "t$index",
                    startedAt = JANUARY_2026 - index * 86_400_000L,
                    endedAt = JANUARY_2026 - index * 86_400_000L + 45 * 60_000L,
                    completed = index != 1,
                    setsCompleted = 18,
                    setsSkipped = if (index == 1) 4 else 0,
                    exerciseCount = 6,
                    volumeKg = 3_240.0,
                )
            },
            mostPerformed = listOf("barbell bench press", "barbell decline wide-grip press"),
            loading = false,
        )
    }
}
