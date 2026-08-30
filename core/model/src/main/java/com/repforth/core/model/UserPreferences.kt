package com.repforth.core.model

/**
 * The user's non-secret settings (§7).
 *
 * Lives in `core:model`, not in `core:datastore`, for the same reason Room
 * entities do not: a screen that reads the theme should not have to depend on
 * the module that decides how preferences are written to disk.
 *
 * Nothing secret belongs in this type. API keys are encrypted separately and
 * must never reach ordinary DataStore (§7, §20) — if a field here is ever
 * tempting to use for a credential, that is the signal to stop.
 */
data class UserPreferences(
    val themeMode: ThemeMode,
    /** `null` means "follow the system locale" — the default, not an absence. */
    val language: Language?,
    val unitSystem: UnitSystem,
    val keepScreenOn: Boolean,
    val reducedMotion: Boolean,
    val hapticsEnabled: Boolean,
    val onboardingComplete: Boolean,
    val mediaWifiOnly: Boolean = true,
) {
    companion object {
        /**
         * What a fresh install gets.
         *
         * These are asserted by a test, because a wrong default is invisible
         * until someone installs the app for the first time — by which point it
         * has already shipped.
         *
         * [keepScreenOn] defaults on: the screen going dark mid-set is a worse
         * failure than the battery cost, and §12 puts the workout surface first.
         * [reducedMotion] defaults off because the platform's own accessibility
         * setting is honoured separately; this is a user override on top of it.
         */
        val Default = UserPreferences(
            themeMode = ThemeMode.SYSTEM,
            language = null,
            unitSystem = UnitSystem.METRIC,
            keepScreenOn = true,
            reducedMotion = false,
            hapticsEnabled = true,
            onboardingComplete = false,
            mediaWifiOnly = true,
        )
    }
}

/** Dark-first is the design direction (§12), but the system choice is honoured. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** Resolves to an actual appearance, given what the system currently is. */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }
}

/**
 * Display only. Weights are stored in kilograms and durations in milliseconds
 * regardless (§7) — this converts at the edge, so no stored number ever depends
 * on what the user had selected when they logged it.
 */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
}
