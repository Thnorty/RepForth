package com.repforth.feature.session

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.repforth.core.wearprotocol.WearAlert
import com.repforth.core.wearprotocol.WearAlertMessage
import com.repforth.core.wearprotocol.WearAssets
import com.repforth.core.wearprotocol.WearPaths
import com.repforth.core.wearprotocol.withMediaAttribution
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
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

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
    suspend fun publish(
        snapshot: SessionSnapshot?,
        names: Map<String, String>,
        thumbnail: ByteArray? = null,
        attribution: String? = null,
    ) {
        // Stamped at publish, not at composition: the watch measures the rest
        // against this, so it has to be the clock reading that goes on the wire.
        val state = snapshot?.toWearState(names, SystemClock.elapsedRealtime()) ?: return
        // §6's notice rides with the picture and never without it. Applied here
        // rather than in the projection because the projection cannot see whether
        // an asset was attached, and the two must agree.
        publish(state.withMediaAttribution(attribution, thumbnail != null), thumbnail)
    }

    /**
     * [thumbnail] is the current exercise's still image, as §11 asks for it.
     *
     * An `Asset` rather than more bytes in the payload, which is what §11 names
     * it as and what the Data Layer wants: assets are transferred out of band
     * and de-duplicated by content hash, so republishing the same exercise's
     * snapshot several times a minute re-sends the JSON and not the picture.
     *
     * Sent as downloaded, at the upstream 180x180. There is no resize step and
     * there must not be one: §6 caps the resolution at exactly that, the file is
     * about 6 KB, and re-encoding would cost an image pipeline in a foreground
     * service to make a small thing slightly smaller.
     */
    suspend fun publish(state: WearWorkoutState, thumbnail: ByteArray? = null) {
        val request = PutDataRequest.create(PATH).apply {
            data = json.encodeToString(state).toByteArray()
            // Absent rather than empty when there is none. The watch draws its
            // icon for a missing asset, which is the same thing the phone draws
            // for a missing image, so the two devices agree about "not here".
            thumbnail?.let { putAsset(WearAssets.THUMBNAIL, Asset.createFromBytes(it)) }
            // Rest countdowns and set changes are worth a battery wake-up; the
            // Data Layer otherwise batches, and a watch showing the previous set
            // is exactly what the revision check spends its time refusing.
            setUrgent()
        }

        try {
            dataClient.putDataItem(request).await()
            // Logged on success, not only on failure. "No error in the log" is
            // not evidence that a snapshot crossed -- it is equally consistent
            // with the publish never being attempted, which is exactly the
            // ambiguity this hit during the first hardware test.
            Log.d(
                TAG,
                "Published revision ${state.revision}, phase ${state.phase}, " +
                    "thumbnail ${thumbnail?.size ?: 0} bytes",
            )
        } catch (e: Exception) {
            // Never fatal. §15 keeps the phone workout working whatever the
            // watch is doing, and a failure here means one device is out of
            // range -- not that the set the user just finished should be lost.
            Log.w(TAG, "Could not publish workout state to the watch", e)
        }
    }

    /**
     * Take the workout off the watch, because there is no longer one.
     *
     * **The Data Layer keeps the last value, which is the whole reason state
     * goes over it — and the reason something has to remove that value.** A data
     * item is not a message: once written it stays until it is overwritten or
     * deleted, so a watch whose phone finished a workout an hour ago still finds
     * it there on the next cold start and shows it as live.
     *
     * Found on hardware. The wrist was showing a paused workout from four days
     * earlier, and pressing its buttons did nothing — correctly, since the phone
     * refuses commands for a session it is not running, but from the outside it
     * is a remote that has stopped working.
     *
     * The watch has always expected this: `WearStateListenerService` handles
     * `TYPE_DELETED` and `WearWorkoutStore.onDataItem` treats an empty payload as
     * "the phone deleted it". Both were written against a deletion nobody
     * performed — the same shape as the exclusions with no editor and the watch
     * action with no button, one layer down.
     */
    suspend fun clear() {
        try {
            dataClient.deleteDataItems(Uri.parse("wear://*$PATH")).await()
            Log.d(TAG, "Cleared the published workout")
        } catch (e: Exception) {
            // Never fatal, like publish. §15: the phone's workout does not
            // depend on the watch hearing about it, and this runs as a session
            // ends -- the least useful moment to take anything down.
            Log.w(TAG, "Could not clear the published workout", e)
        }
    }

    /**
     * Tell the watch a timer reached zero (§3).
     *
     * A message rather than a field on the snapshot, and the reasoning is this
     * class's own argument for state run backwards. The Data Layer keeps the
     * last value, which is why the snapshot goes over it — a watch that was out
     * of range still learns the current set. An alert wants the opposite: it is
     * true at an instant, and a wrist buzzing on reconnect for a rest that ended
     * while the watch sat in a drawer is worse than one that never buzzed at
     * all. A message has no memory, and here that is the whole point.
     *
     * Sent only to a **nearby** node, for the reason `WearWorkoutStore` gives on
     * the other side: `connectedNodes` answers what this device knows about, not
     * what it can reach, and it keeps returning the peer with every radio off so
     * the Data Layer can route through the cloud. A buzz that arrives by that
     * route arrives after the rest it was announcing.
     *
     * Never fatal, like [publish], and for the same §15 reason: the workout on
     * the phone does not depend on a watch hearing about it.
     */
    suspend fun alert(sessionId: String, kind: WearAlert) {
        try {
            val nodes = nodeClient.connectedNodes.await().filter { it.isNearby }
            if (nodes.isEmpty()) {
                Log.d(TAG, "Not alerting: no watch nearby")
                return
            }
            val message = WearAlertMessage(sessionId = sessionId, alert = kind)
            val payload = json.encodeToString(message).toByteArray()
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, ALERT_PATH, payload).await()
            }
            Log.d(TAG, "Alerted ${nodes.size} watch(es): $kind")
        } catch (e: Exception) {
            Log.w(TAG, "Could not alert the watch", e)
        }
    }

    companion object {
        /**
         * §11's paths, declared once in the protocol both sides compile against.
         *
         * These were literals here and again in the watch's store — two strings
         * that must be equal, in two modules that never see each other.
         */
        const val PATH = WearPaths.STATE
        const val ALERT_PATH = WearPaths.ALERT
        const val COMMAND_PATH = WearPaths.COMMAND


        private const val TAG = "WearBridge"
    }
}
