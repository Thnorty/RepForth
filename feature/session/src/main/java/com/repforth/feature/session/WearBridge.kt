package com.repforth.feature.session

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.repforth.core.wearprotocol.WearWorkoutState
import com.repforth.core.wearsync.toWearState
import com.repforth.core.workout.SessionSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * Publishes the running workout to the watch (§11).
 *
 * `DataClient` rather than `MessageClient` for state, because the Data Layer
 * keeps the last value: a watch that was out of range, or asleep, or only just
 * put on, gets the current snapshot when it reconnects instead of nothing until
 * the next set. §11 asks for the *latest* snapshot at a path, which is what a
 * data item is; a message is an event and has no memory.
 *
 * There is no pairing check here and no node discovery. Publishing to a path
 * nobody is listening to is free, and §11 puts reachability on the watch's side
 * of the problem — it is the device that has to say "phone not connected".
 */
@Singleton
class WearBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    /**
     * Encoded here rather than as a `DataMap` of loose keys.
     *
     * One serialised blob means the wire format is the `@Serializable` class
     * and nothing else — the protocol module is the single description of it,
     * and a field cannot be added on one side as a bare string key that the
     * other never reads.
     */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Send the current state, or nothing when there is no workout.
     *
     * A null projection is not an error: it is the phone saying the session is
     * idle or gone, and §11's answer to that is the watch's "no workout"
     * screen, which is the absence of a snapshot rather than a snapshot of an
     * absence.
     */
    suspend fun publish(snapshot: SessionSnapshot?, names: Map<String, String>) {
        val state = snapshot?.toWearState(names) ?: return
        publish(state)
    }

    suspend fun publish(state: WearWorkoutState) {
        val request = PutDataRequest.create(PATH).apply {
            data = json.encodeToString(state).toByteArray()
            // Rest countdowns and set changes are worth a battery wake-up; the
            // Data Layer otherwise batches, and a watch showing the previous set
            // is exactly what the revision check spends its time refusing.
            setUrgent()
        }

        try {
            dataClient.putDataItem(request).await()
        } catch (e: Exception) {
            // Never fatal. §15 keeps the phone workout working whatever the
            // watch is doing, and a failure here means one device is out of
            // range -- not that the set the user just finished should be lost.
            Log.w(TAG, "Could not publish workout state to the watch", e)
        }
    }

    companion object {
        /** §11 names this path. */
        const val PATH = "/workout/active"

        /** Commands travel the other way, over MessageClient. */
        const val COMMAND_PATH = "/workout/command"

        private const val TAG = "WearBridge"
    }
}
