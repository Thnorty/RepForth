package com.repforth.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.repforth.core.model.Language
import java.util.Locale

/**
 * Renders its content in the language the user chose (§13).
 *
 * The preference was being stored and then ignored: picking Türkçe changed a row
 * in DataStore and nothing on screen. Resources are resolved from the context
 * and configuration Compose provides, so overriding both here is what makes the
 * choice mean anything.
 *
 * Done this way rather than by recreating the activity, or by adding AppCompat
 * for `setApplicationLocales`: this app has no AppCompat and needs none, and a
 * recreate would drop the running workout's screen state to change a label. The
 * cost is that only Compose content is translated — a system dialog this app
 * shows would still follow the device — which is acceptable while every screen
 * is Compose, and is the thing to revisit if that stops being true.
 *
 * [Language] `null` means follow the device, which is the default, so the
 * context is left exactly as it arrived rather than being overridden with
 * whatever the device currently is.
 */
@Composable
fun LocalizedContent(language: Language?, content: @Composable () -> Unit) {
    if (language == null) {
        content()
        return
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val localized = remember(language, configuration) {
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val updated = Configuration(configuration).apply { setLocale(locale) }
        context.createConfigurationContext(updated) to updated
    }

    CompositionLocalProvider(
        LocalContext provides localized.first,
        LocalConfiguration provides localized.second,
        content = content,
    )
}
