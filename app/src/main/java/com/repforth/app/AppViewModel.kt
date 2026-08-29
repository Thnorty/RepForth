package com.repforth.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.userdata.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Which of the two things the app can be showing.
 *
 * [Loading] is a real state, not a formality: the profile is read from disk, so
 * for the first frames the app genuinely does not know whether onboarding has
 * happened. Guessing would show the questionnaire to someone who finished it
 * last week, for as long as the read takes.
 */
sealed interface AppUiState {
    data object Loading : AppUiState

    data object Onboarding : AppUiState

    data object Ready : AppUiState
}

/**
 * Decides whether the user sees onboarding or the app.
 *
 * The decision is derived from whether a profile exists, and from nothing else.
 * That is what makes finishing onboarding work without any navigation: the
 * questionnaire writes a profile, this flow emits [AppUiState.Ready], and the
 * shell replaces itself. There is no completion flag that could disagree with
 * the database, and no way to reach the app with a profile the rules engine
 * cannot read.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    profiles: ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = profiles.observeProfile()
        .map { profile ->
            if (profile == null) AppUiState.Onboarding else AppUiState.Ready
        }
        .stateIn(
            scope = viewModelScope,
            // Eagerly, not WhileSubscribed: this decides the first frame, so the
            // read should already be in flight before anything collects it.
            started = SharingStarted.Eagerly,
            initialValue = AppUiState.Loading,
        )
}
