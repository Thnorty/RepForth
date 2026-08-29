package com.repforth.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
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
 * The preference was stored and then ignored: picking Türkçe changed a row in
 * DataStore and nothing on screen. Resources are resolved from the context
 * Compose provides, so that is what has to change.
 *
 * **It has to stay a wrapper around the activity.** The first version provided
 * `createConfigurationContext(...)` directly, which returns a bare `ContextImpl`
 * — and everything that needs the activity finds it by walking `baseContext` up
 * the `ContextWrapper` chain. Severing that chain crashed every screen with a
 * ViewModel the moment a language was chosen:
 *
 * ```
 * Expected an activity context for creating a HiltViewModelFactory
 * ```
 *
 * A `ContextWrapper` whose base *is* the activity keeps that walk working while
 * still answering [getResources] in the chosen language.
 *
 * Done this way rather than by recreating the activity, or by adding AppCompat
 * for `setApplicationLocales`: this app has no AppCompat and needs none, and a
 * recreate would drop a running workout's screen state to change a label. The
 * cost is that only Compose content follows the choice — which is every screen
 * this app has — and that `Locale.getDefault()` still reports the device's
 * locale, so a number formatted through it groups digits the device's way. That
 * is the smaller wrong of the two: setting the JVM default from inside
 * composition is a global mutation on a shared process, and it is what makes
 * Turkish famous for breaking `"I".lowercase()` in unrelated code.
 */
@Composable
fun LocalizedContent(language: Language?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Null means follow the device, which is the default. It still goes through
    // the provider with the values unchanged rather than calling content()
    // directly.
    //
    // That is not tidiness. A composable that calls its content in two
    // structurally different places recomposes it from scratch when it moves
    // between them, and the subtree here contains the NavHost — so switching
    // to or from "System" threw away the back stack and dropped the user on
    // Today, while switching between two real languages did not.
    val localizedConfiguration = remember(language, configuration) {
        if (language == null) {
            configuration
        } else {
            Configuration(configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
        }
    }
    val localizedContext = remember(context, localizedConfiguration, language) {
        if (language == null) context else LocalizedContext(context, localizedConfiguration)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        content = content,
    )
}

/**
 * The activity, answering in a different language.
 *
 * Everything is delegated to [base] except [getResources]; keeping the activity
 * as the base is what lets `findActivity()`-style lookups still succeed.
 */
private class LocalizedContext(
    base: Context,
    configuration: Configuration,
) : ContextWrapper(base) {

    private val localizedResources: Resources =
        base.createConfigurationContext(configuration).resources

    override fun getResources(): Resources = localizedResources
}
