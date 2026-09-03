package com.repforth.feature.session

import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.wearprotocol.WearAdmission
import com.repforth.core.wearprotocol.WearCommand
import com.repforth.core.wearsync.toSessionCommand
import com.repforth.core.wearsync.toWearState
import com.repforth.core.wearprotocol.admit
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Receives what the watch asks for, and decides whether to do it (§11).
 *
 * A `WearableListenerService` rather than a listener registered by a screen:
 * the phone must answer whether or not its display is on, and §11's whole
 * premise is a wrist controlling a workout the user is not looking at the phone
 * for.
 *
 * **Every command goes through `admit` before it reaches the engine.** That is
 * §20's "cannot silently mutate stale state", and the response to a refusal is
 * to republish the current snapshot rather than to report an error — the watch
 * had an old picture and the cure is a new one.
 */
@AndroidEntryPoint
class WearCommandService : WearableListenerService() {

    @Inject lateinit var controller: SessionController

    @Inject lateinit var exercises: ExerciseRepository

    @Inject lateinit var bridge: WearBridge

    private val scope = CoroutineScope(SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearBridge.COMMAND_PATH) return

        val command = decode(event) ?: return
        Log.i(TAG, "Received ${command.action} expecting revision ${command.expectedRevision}")
        scope.launch { apply(command) }
    }

    private fun decode(event: MessageEvent): WearCommand? = try {
        json.decodeFromString<WearCommand>(String(event.data))
    } catch (e: Exception) {
        // A message this phone cannot parse is a watch running a build this one
        // does not understand. Dropping it is right: there is no partial
        // reading of a command that is safe to apply.
        Log.w(TAG, "Unreadable command from the watch", e)
        null
    }

    private suspend fun apply(command: WearCommand) {
        val names = names()
        val current = controller.state.value?.toWearState(names, SystemClock.elapsedRealtime())

        if (current == null) {
            // Nothing is running, so there is nothing to be stale about and
            // nothing to apply. Publishing would be publishing an absence.
            Log.i(TAG, "Ignoring ${command.action}: no workout in progress")
            return
        }

        when (val decision = admit(command, current)) {
            is WearAdmission.AnswerWithCurrentState -> {
                // Not an error, and not reported as one. The watch is behind;
                // send it the truth and let it correct itself.
                Log.i(TAG, "Refused ${command.action}: ${decision.reason}")
                bridge.publish(current)
            }

            WearAdmission.Apply -> {
                Log.i(TAG, "Applying ${command.action} at revision ${command.expectedRevision}")
                val updated = controller.dispatch(command.toSessionCommand())
                Log.i(TAG, "Applied ${command.action}; revision is now ${updated?.revision}")
                // Republish either way. The engine may also have refused it --
                // for a duplicate id, or a phase that does not allow it -- and
                // the watch learns that the same way it learns everything else.
                bridge.publish(updated, names)
            }
        }
    }

    /** Ids to names, for the one exercise the watch will show. */
    private suspend fun names(): Map<String, String> {
        val snapshot = controller.state.value ?: return emptyMap()
        return snapshot.exercises
            .mapNotNull { session ->
                exercises.find(session.exerciseId)?.let { session.exerciseId.value to it.name }
            }
            .toMap()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "WearCommandService"
    }
}
