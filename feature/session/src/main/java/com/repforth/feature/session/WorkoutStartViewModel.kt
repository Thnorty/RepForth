package com.repforth.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.userdata.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What tapping "start" on a plan should do, decided before leaving the list.
 *
 * The conflict used to be raised inside the workout screen, which meant being
 * dropped into a workout you had not chosen and *then* being asked about it.
 * Asking first is the honest order: the question is whether to go somewhere,
 * so it belongs where you are, not where you would have ended up.
 *
 * This lives above the navigation graph rather than in either list screen,
 * because both Today and Plans start plans and the answer must not depend on
 * which one was used.
 */
@HiltViewModel
class WorkoutStartViewModel @Inject constructor(
    private val controller: SessionController,
    private val templates: TemplateRepository,
) : ViewModel() {

    private val _intent = MutableStateFlow<StartIntent?>(null)
    val intent: StateFlow<StartIntent?> = _intent.asStateFlow()

    /**
     * The name of the workout in progress, for the app bar.
     *
     * Null until something has restored the session, which the workout screen
     * does as it opens — so by the time this is on screen it has resolved. Null
     * also for a workout with no plan behind it, where the bar keeps its
     * generic title rather than inventing one.
     */
    val activeWorkoutName: StateFlow<String?> = controller.state
        .map { snapshot -> snapshot?.templateId?.let { templates.find(it)?.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * The user tapped a plan.
     *
     * [SessionController.restore] rather than `start`: this has to know whether
     * something is running *without* starting anything, since the whole point
     * is to ask first. It is idempotent and reads only.
     */
    fun request(templateId: String) {
        viewModelScope.launch {
            val running = controller.restore()?.takeIf { !it.phase.isTerminal }

            _intent.value = when {
                // Nothing in the way, or the plan that is already going —
                // tapping the running workout means "take me back to it".
                running == null || running.templateId == templateId ->
                    StartIntent.Open(templateId)

                else -> StartIntent.Conflict(
                    requestedTemplateId = templateId,
                    runningName = running.templateId?.let { templates.find(it)?.name },
                )
            }
        }
    }

    /** Keep the running workout — and go to it, since that is what was asked. */
    fun keepRunning() {
        // Null template: open whatever is running rather than starting a plan.
        _intent.value = StartIntent.Open(templateId = null)
    }

    /**
     * End the running workout and begin the one that was tapped.
     *
     * Everything already recorded on the abandoned session is kept, which is
     * what `Abandon` means in §10 — the dialog says so.
     */
    fun discardAndStart() {
        val requested = (_intent.value as? StartIntent.Conflict)?.requestedTemplateId ?: return
        viewModelScope.launch {
            controller.abandonAndStart(requested)
            _intent.value = StartIntent.Open(requested)
        }
    }

    /**
     * The question was dismissed without being answered.
     *
     * Nothing starts, nothing ends, and nothing navigates — the user stays on
     * the list they were already looking at. That is only a safe answer because
     * this is asked before leaving it; the same gesture inside the workout
     * screen would have had nowhere harmless to land.
     */
    fun cancel() {
        _intent.value = null
    }

    /** Called once the shell has navigated, so the intent cannot fire twice. */
    fun consumed() {
        _intent.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** What the shell should do about a plan the user tapped. */
sealed interface StartIntent {

    /**
     * Go to the workout screen.
     *
     * A null [templateId] means "whatever is running" rather than "nothing" —
     * the screen resumes instead of starting.
     */
    data class Open(val templateId: String?) : StartIntent

    /** A different workout is running. Ask, and do not move yet. */
    data class Conflict(
        val requestedTemplateId: String,
        val runningName: String?,
    ) : StartIntent
}
