package com.repforth.feature.body

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
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

/** What TalkBack would find on a measurement's history and on the check-in list. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class BodyListsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun measurement_english() = check("Measurement", ENGLISH) { MeasurementDetailScreen(state = MeasurementDetailFixtures.WAIST) }

    @Test
    fun measurement_turkish() = check("Measurement", TURKISH) { MeasurementDetailScreen(state = MeasurementDetailFixtures.WAIST) }

    @Test
    fun check_ins_english() = check("Check-ins", ENGLISH) { CheckInListScreen(rows = checkInRows(BodyFixtures.CHECK_INS)) }

    private fun check(screen: String, locale: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent { RepForthPreviewHost { content() } }
        compose.assertScreenIsAccessible("$screen ($locale)")
    }
}
