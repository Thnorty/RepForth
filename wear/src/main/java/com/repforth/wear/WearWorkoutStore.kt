package com.repforth.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.Wearable
import com.repforth.core.wearprotocol.WEAR_PROTOCOL_VERSION
import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearCommand
import com.repforth.core.wearprotocol.WearWorkoutState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * Everything the watch knows, which is only ever what the phone last said.
 *
 * A singleton because two things write to it — the listener service, which runs
 * whether or not the app is open, and the screen, which reads the last value
 * when it opens. §11 gives the watch no engine and no storage, so this holds a
 * snapshot in memory and nothing else: a watch that had its own copy of the
 * workout would be a second source of truth, which is exactly what the revision
 * protocol exists to avoid.
 *
 * Nothing here is persisted. If the process dies the state comes back from the
 * Data Layer, which keeps the last data item — that is why §11 puts the
 * snapshot on `DataClient` rather than sending it as a message.
 */
@Singleton
class WearWorkoutStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    private val _state = MutableStateFlow<WearWorkoutState?>(null)

    /** The last snapshot the phone published, or null when it has said nothing. */
    val state: StateFlow<WearWorkoutState?> = _state.asStateFlow()

    private val _phoneReachable = MutableStateFlow(true)

    /**
     * Whether a phone is currently reachable.
     *
     * §11 makes this the watch's problem rather than the phone's: the phone
     * publishes to a path and does not care who is listening, and it is the
     * watch that has to say "phone not connected" and disable everything that
     * modifies.
     */
    val phoneReachable: StateFlow<Boolean> = _phoneReachable.asStateFlow()

    /** Called by the listener service when a data item changes. */
    fun onDataItem(item: DataItem) {
        if (item.uri.path != PATH) return
        // A data item with no payload is one the phone deleted; the workout is
        // over and the terminal snapshot was already delivered before it went.
        val payload = item.data ?: return
        val decoded = decode(payload)
        Log.d(TAG, "Received revision ${decoded?.revision}, phase ${decoded?.phase}")
        _state.value = decoded
    }

    /** Read whatever is already there, for a screen opening cold. */
    suspend fun refresh() {
        try {
            val items = dataClient.dataItems.await()
            items.forEach { onDataItem(it) }
            items.release()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the current workout state", e)
        }
        checkReachability()
    }

    /**
     * Whether a node with the phone app on it can be seen right now.
     *
     * Connected nodes rather than paired ones: §11's disconnected screen is
     * about whether a command would arrive, not about whether a phone exists
     * somewhere.
     */
    suspend fun checkReachability() {
        _phoneReachable.value = try {
            nodeClient.connectedNodes.await().isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine whether the phone is reachable", e)
            false
        }
    }

    /**
     * Ask the phone for something.
     *
     * The command carries the revision of the snapshot on screen at the moment
     * it was pressed, which is what lets the phone refuse it if the workout has
     * moved on. Returns false when there was nothing to act on or nobody to
     * send to — the caller uses that to leave the button alone rather than to
     * show an error, because §11's answer to "it did not arrive" is the
     * disconnected screen, not a toast.
     */
    suspend fun send(action: WearAction): Boolean {
        val current = _state.value ?: return false

        val command = WearCommand(
            protocolVersion = WEAR_PROTOCOL_VERSION,
            sessionId = current.sessionId,
            commandId = UUID.randomUUID().toString(),
            // The revision on the screen the user was looking at, not the
            // newest one known -- those are the same thing here, and this is
            // the field that makes them stay the same thing.
            expectedRevision = current.revision,
            sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            action = action,
        )

        return try {
            val nodes = nodeClient.connectedNodes.await()
            _phoneReachable.value = nodes.isNotEmpty()
            if (nodes.isEmpty()) return false

            val payload = json.encodeToString(command).toByteArray()
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, COMMAND_PATH, payload).await()
            }
            Log.d(TAG, "Sent $action at revision ${command.expectedRevision} to ${nodes.size} node(s)")
            true
        } catch (e: Exception) {
            // Not shown to the user. The phone either applied it and will
            // publish a new snapshot, or it did not and the old one still
            // stands -- and both of those are already on screen.
            Log.w(TAG, "Could not send $action to the phone", e)
            _phoneReachable.value = false
            false
        }
    }

    private fun decode(bytes: ByteArray): WearWorkoutState? = try {
        json.decodeFromString<WearWorkoutState>(String(bytes))
    } catch (e: Exception) {
        // A snapshot this build cannot read means the phone is newer. Keeping
        // the previous state is better than blanking the screen mid-set, and
        // the version check on the next command will refuse to act on it.
        Log.w(TAG, "Unreadable workout state from the phone", e)
        _state.value
    }

    private companion object {
        const val TAG = "WearWorkoutStore"

        /** §11 names these paths, and the phone bridge uses the same two. */
        const val PATH = "/workout/active"
        const val COMMAND_PATH = "/workout/command"
    }
}
