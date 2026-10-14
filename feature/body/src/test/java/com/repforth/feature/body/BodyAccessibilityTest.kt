package com.repforth.feature.body

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

/** What TalkBack would find on the Body tab. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class BodyAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun body_english() = check(ENGLISH, bodyState(BodyFixtures.TYPES, BodyFixtures.CHECK_INS))

    @Test
    fun body_turkish() = check(TURKISH, bodyState(BodyFixtures.TYPES, BodyFixtures.CHECK_INS))

    @Test
    fun body_empty() = check(ENGLISH, bodyState(BodyFixtures.TYPES, emptyList()))

    private fun check(locale: String, state: BodyUiState) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent { RepForthPreviewHost { BodyScreen(state = state) } }
        compose.assertScreenIsAccessible("Body ($locale)")
    }
}
