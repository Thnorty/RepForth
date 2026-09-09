package com.repforth.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.repforth.core.wearprotocol.WEAR_PROTOCOL_VERSION
import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearAssets
import com.repforth.core.wearprotocol.WearCommand
import com.repforth.core.wearprotocol.WearPaths
import com.repforth.core.wearprotocol.WearWorkoutState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val ongoing: WearWorkoutNotification,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(context) }

    /**
     * For decoding assets, which cannot happen on the callback that delivers them.
     *
     * The store is a singleton for the life of the process and so is this. There
     * is nothing to cancel: a decode that outlives the screen still leaves the
     * right bitmap in hand for whatever opens next, which is the entire reason
     * the snapshot is held here rather than in a view model.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<WearWorkoutState?>(null)

    /** The last snapshot the phone published, or null when it has said nothing. */
    val state: StateFlow<WearWorkoutState?> = _state.asStateFlow()

    private val _thumbnail = MutableStateFlow<Bitmap?>(null)

    /**
     * The current exercise's still image (§3, §11), or null when there is none.
     *
     * Null covers every reason at once — the phone has not cached it, the
     * manifest has no entry, the user restricted downloads to Wi-Fi — because
     * the screen does the same thing for all of them, which is the same thing
     * the phone does: draw an icon.
     */
    val thumbnail: StateFlow<Bitmap?> = _thumbnail.asStateFlow()

    /** The asset already fetched, so a snapshot per second is not a decode per second. */
    private var thumbnailRef: String? = null

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

        // Read here, synchronously, and not inside the coroutine below. The
        // `DataEventBuffer` this item came from is released the moment
        // `onDataChanged` returns, so the id has to be taken out of it first;
        // the id is a plain string and outlives the buffer, the DataItem does
        // not.
        onThumbnailAsset(item.assets[WearAssets.THUMBNAIL]?.id)
        // §3's way back from the watch face, kept in step here rather than in
        // the listener service: the snapshot arrives two ways -- pushed while
        // nothing is on screen, and pulled by `refresh` when the app opens cold
        // -- and a chip posted on only one of them is missing in exactly the
        // case it exists for.
        ongoing.update(decoded)
    }

    /**
     * Fetch and decode the thumbnail, or drop it.
     *
     * Keyed on the asset id, which the Data Layer derives from the content: the
     * same picture republished with a new snapshot is the same id, so staying on
     * one exercise costs one decode rather than one per publish.
     */
    private fun onThumbnailAsset(assetId: String?) {
        if (assetId == thumbnailRef) return
        thumbnailRef = assetId

        if (assetId == null) {
            _thumbnail.value = null
            return
        }
        scope.launch {
            val decoded = try {
                val response = dataClient.getFdForAsset(Asset.createFromRef(assetId)).await()
                val bitmap = response.inputStream?.use { BitmapFactory.decodeStream(it) }
                response.release()
                bitmap
            } catch (e: Exception) {
                // A picture is the one thing on this screen that can be missing
                // without the screen being wrong, so this is a log and nothing
                // else. The name, the set and the countdown are all still true.
                Log.w(TAG, "Could not read the exercise thumbnail", e)
                null
            }
            // Only if it is still the one being asked for -- a slow decode must
            // not overwrite a newer exercise's picture with an older one.
            if (thumbnailRef == assetId) _thumbnail.value = decoded
        }
    }

    /**
     * The phone removed the workout, so the watch has none.
     *
     * Clears the snapshot, the thumbnail and the watch-face chip together —
     * three things that were all about a session that no longer exists. Leaving
     * any of them is the stale-state failure this whole path exists to avoid:
     * a chip that reopens a finished workout, or an exercise screen whose
     * buttons the phone will refuse.
     */
    fun onWorkoutGone() {
        Log.d(TAG, "The phone cleared the workout")
        _state.value = null
        onThumbnailAsset(null)
        ongoing.update(null)
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
     * Whether a phone running RepForth can be reached right now.
     *
     * **Not `NodeClient.connectedNodes`.** That was the first implementation
     * and it is wrong in a way that looks right: with Bluetooth off, and the
     * system's own `WearableService` reporting `0 connected out of 1`, it kept
     * returning one node. It reports what this device knows about, not what it
     * can talk to, so the disconnected screen was unreachable and every control
     * stayed live on a watch that could not send anything.
     *
     * A capability filtered by [CapabilityClient.FILTER_REACHABLE] confirms the
     * peer is a phone with this app on it — but **it is still not enough on its
     * own**. With the phone's radios all switched off, and the watch's own
     * `WearableService` reporting `0 connected out of 1`, both that call and
     * `connectedNodes` kept returning one node: Google keeps an entry for the
     * peer so it can route over the cloud when both devices are online.
     *
     * [Node.isNearby] is the field that separates the two. It is true only for
     * a node reachable directly — Bluetooth, or Wi-Fi on the same network —
     * and false when the only path left is a cloud round trip. A workout remote
     * wants the direct link: §11's disconnected screen is about whether pressing
     * "complete" will do anything in the next second, not about whether a
     * message could eventually be delivered.
     */
    suspend fun checkReachability() {
        val reachable = try {
            val capability = capabilityClient
                .getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
            val nearby = capability.nodes.filter { it.isNearby }
            Log.d(
                TAG,
                "Reachability check: ${capability.nodes.size} node(s), ${nearby.size} nearby",
            )
            nearby.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine whether the phone is reachable", e)
            false
        }
        if (reachable != _phoneReachable.value) {
            Log.i(TAG, "Phone reachable changed to $reachable")
        }
        _phoneReachable.value = reachable
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
            // Nearby, for the same reason `checkReachability` insists on it: a
            // node that is merely known would set this back to true and put
            // live controls on a watch that cannot reach anything.
            val nodes = nodeClient.connectedNodes.await().filter { it.isNearby }
            _phoneReachable.value = nodes.isNotEmpty()
            if (nodes.isEmpty()) {
                Log.i(TAG, "Not sending $action: no phone nearby")
                return false
            }

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

        /** §11's paths, from the protocol module the phone bridge also reads. */
        const val PATH = WearPaths.STATE
        const val COMMAND_PATH = WearPaths.COMMAND


        /** Declared by the phone in `res/values/wear.xml`. */
        const val PHONE_CAPABILITY = "repforth_phone"
    }
}
