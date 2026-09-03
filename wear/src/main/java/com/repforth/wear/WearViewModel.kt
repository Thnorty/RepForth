package com.repforth.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
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
        pollReachability(),
    ) { workout, reachable, _ ->
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

    /**
     * Notices the phone going away, rather than waiting to be told.
     *
     * Nothing pushes a disconnection to an app: the Data Layer simply stops
     * delivering. Before this, `phoneReachable` was refreshed on start, on
     * resume, and after a send failed — so a watch whose phone went out of
     * range kept showing live-looking controls until someone pressed one and it
     * silently did nothing.
     *
     * **That was a §20 violation, found by turning Bluetooth off mid-rest.**
     * The clause is that a disconnected watch "clearly becomes read-only", and
     * a screen that only discovers the truth when you touch it is not clear
     * about anything.
     *
     * Polling rather than a capability listener because the question is
     * genuinely "can I reach a node right now", which is what
     * `NodeClient.connectedNodes` answers directly. It runs only while
     * something is collecting the state — `WhileSubscribed` cancels this with
     * everything else — so a watch on its face is not paying for it.
     */
    private fun pollReachability(): Flow<Unit> = flow {
        while (true) {
            store.checkReachability()
            emit(Unit)
            delay(POLL_INTERVAL_MS)
        }
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

        /**
         * Often enough that a user who glances down after walking away sees the
         * truth, rarely enough to be invisible on a battery: a node lookup is
         * a local IPC call, not radio traffic.
         */
        const val POLL_INTERVAL_MS = 3_000L
    }
}
