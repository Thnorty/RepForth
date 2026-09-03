package com.repforth.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the watch shows, and the one thing it can do about it.
 *
 * Thin on purpose. §11 gives the watch no engine, so there is nothing here to
 * decide except which of the five screens applies — every state change arrives
 * from the phone, and every button is a message rather than a local edit.
 */
data class WearUiState(
    val workout: WearWorkoutState? = null,
    val phoneReachable: Boolean = true,
) {
    /**
     * Which of §11's five screens this is.
     *
     * Disconnected outranks everything, including a workout that is still on
     * screen from a moment ago. That is the honest ordering: the snapshot may
     * be perfectly current, but nothing the user presses will arrive, and a
     * screen full of live-looking buttons that do nothing is worse than one
     * that says why.
     */
    val screen: WearScreen
        get() = when {
            !phoneReachable -> WearScreen.Disconnected
            workout == null -> WearScreen.NoWorkout
            workout.phase == WearPhase.Rest -> WearScreen.Rest
            workout.phase == WearPhase.Finished -> WearScreen.Finished
            workout.phase == WearPhase.Abandoned -> WearScreen.Finished
            else -> WearScreen.Exercise
        }

    /**
     * Whether the controls may be used.
     *
     * §11: "When disconnected, the last snapshot may remain visible but all
     * modifying actions are disabled and clearly marked unavailable."
     */
    val controlsEnabled: Boolean get() = phoneReachable && workout != null
}

/** §11's five screens. */
enum class WearScreen { Disconnected, NoWorkout, Exercise, Rest, Finished }

@HiltViewModel
class WearViewModel @Inject constructor(
    private val store: WearWorkoutStore,
) : ViewModel() {

    val uiState: StateFlow<WearUiState> = combine(
        store.state,
        store.phoneReachable,
    ) { workout, reachable ->
        WearUiState(workout = workout, phoneReachable = reachable)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = WearUiState(),
    )

    init {
        // The Data Layer keeps the last value, so an app opened cold has a
        // current snapshot waiting for it rather than nothing until the next
        // set. Asking for it here is what turns that into a screen.
        viewModelScope.launch { store.refresh() }
    }

    fun onAction(action: WearAction) {
        viewModelScope.launch { store.send(action) }
    }

    /** Called when the screen comes back, since reachability can change unseen. */
    fun onResumed() {
        viewModelScope.launch { store.checkReachability() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
