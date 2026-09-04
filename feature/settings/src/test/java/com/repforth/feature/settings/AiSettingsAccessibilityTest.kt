package com.repforth.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
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
 * What TalkBack would find on the AI provider screen.
 *
 * The last screen in the app with no accessibility check, and the one with the
 * most to check: a provider choice, a masked key field, a model field, an
 * address field, a slider, a connection test and a destructive action. It also
 * holds the only credential the app stores, so a control here that announces
 * nothing is the most expensive kind.
 *
 * The plan argued it could be left out because "its interesting states are a
 * typed key and a connection result rather than a layout under pressure", which
 * is a fair case against a *golden* and no case at all against this. Three of
 * the nine defects found by hand on a device were on this screen.
 *
 * The slider is what it caught. `ai_timeout` — "Request timeout" — had been
 * written and translated and drawn nowhere, so the control had a value above it
 * and no name on it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class AiSettingsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ai_settings_english() = check(ENGLISH)

    @Test
    fun ai_settings_turkish() = check(TURKISH)

    /**
     * The half of the screen behind "Advanced".
     *
     * The timeout slider renders only once that is expanded, so a test that
     * stopped at the top of the screen would never have reached the control
     * this class was written for — the same trap the equipment dialog's
     * uncommon rows set in `SettingsAccessibilityTest`.
     */
    @Test
    fun ai_settings_advanced_english() = check(ENGLISH, expandAdvanced = true)

    @Test
    fun ai_settings_advanced_turkish() = check(TURKISH, expandAdvanced = true)

    private fun check(locale: String, expandAdvanced: Boolean = false) {
        render(locale, expandAdvanced)

        if (expandAdvanced) {
            // Stated rather than assumed. `assertScreenIsAccessible` passes on
            // a tree that does not contain the thing being checked, so without
            // this the advanced runs would be green having inspected the
            // collapsed screen twice — which is exactly how a check that
            // reaches nothing looks from the outside.
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            compose.onNodeWithText(context.getString(R.string.ai_timeout)).assertExists()
        }

        compose.assertScreenIsAccessible("AI provider ($locale, advanced=$expandAdvanced)")
    }

    /**
     * `advancedShown` is set rather than tapped.
     *
     * It is hoisted into the state, so the toggle only moves when the view
     * model answers — and a no-op callback here means the tap does nothing at
     * all. That is unlike the two dialogs in `SettingsAccessibilityTest`, which
     * hold their own state and can therefore be opened the way a person does.
     */
    private fun render(locale: String, advancedShown: Boolean) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)

        compose.setContent {
            RepForthPreviewHost {
                AiSettingsScreen(
                    // The generic provider, because it draws the most: it is the
                    // only one with an address field.
                    state = AiSettingsUiState(
                        settings = ProviderSettings.Default.copy(
                            provider = ProviderId.OPENAI_COMPATIBLE,
                        ),
                        baseUrl = "https://example.invalid/v1/",
                        advancedShown = advancedShown,
                    ),
                    onProviderChange = {},
                    onKeyChange = {},
                    onSaveKey = {},
                    onDeleteKey = {},
                    onModelChange = {},
                    onBaseUrlChange = {},
                    onTimeoutChange = {},
                    onAdvancedToggled = {},
                    onTestConnection = {},
                    onDeleteEverything = {},
                    onMessageShown = {},
                )
            }
        }
    }
}
