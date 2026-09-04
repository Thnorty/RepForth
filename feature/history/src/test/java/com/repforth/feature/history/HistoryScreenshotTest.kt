package com.repforth.feature.history

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.Muscle
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_COMPARISON
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.screenshotPath
import com.repforth.core.workout.ProgressSummary
import com.repforth.core.workout.WorkoutSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Progress, which is mostly numbers beside labels.
 *
 * That pairing is the risk: `RepForthNumeric` is deliberately large and its
 * label sits under or beside it, so a Turkish label at 200% is exactly the case
 * that pushes one past the other. Nothing on this screen had been rendered
 * anywhere but a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class HistoryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun progress_english() = capture("progress-en", POPULATED)

    @Test
    fun progress_turkish() = capture("progress-tr", POPULATED, locale = TURKISH)

    @Test
    fun progress_large_text() = capture("progress-en-2x", POPULATED, fontScale = 2f)

    @Test
    fun progress_turkish_large_text() =
        capture("progress-tr-2x", POPULATED, locale = TURKISH, fontScale = 2f)

    /** Nothing done yet is the state every new install sees first. */
    @Test
    fun progress_empty() = capture("progress-empty-en", HistoryUiState(loading = false))

    private fun capture(
        name: String,
        state: HistoryUiState,
        locale: String? = null,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setQualifiers("+${locale ?: ENGLISH}")
        RuntimeEnvironment.setFontScale(fontScale)

        compose.setContent { RepForthPreviewHost { HistoryScreen(state = state) } }
        compose.onRoot().captureRoboImage(screenshotPath(name), SCREENSHOT_COMPARISON)
    }

    private companion object {
        /** A fixed instant, so a golden does not change with the clock. */
        const val JANUARY_2026 = 1_767_225_600_000L

        val POPULATED = HistoryUiState(
            progress = ProgressSummary(
                workouts = 42,
                workoutsThisWeek = 3,
                daysThisWeek = 3,
                streakWeeks = 6,
                // Large on purpose: a five-figure number is what runs into the
                // label beside it.
                totalVolumeKg = 128_450.0,
                totalSets = 1_260,
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
            topMuscles = listOf(Muscle.PECTORALS, Muscle.QUADRICEPS, Muscle.LATS),
            // Three of four, so the bar shows both states rather than a full or
            // empty row that proves nothing about the unfilled segment.
            weeklyTarget = 4,
            loading = false,
        )
    }
}
