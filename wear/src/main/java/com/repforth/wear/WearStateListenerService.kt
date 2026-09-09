package com.repforth.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.repforth.core.wearprotocol.WearAlertMessage
import com.repforth.core.wearprotocol.WearPaths
import com.repforth.core.wearprotocol.appliesTo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * Receives what the phone sends: the workout snapshot, and a timer reaching zero.
 *
 * A service rather than a listener registered by the screen, because the app is
 * usually not open: a watch spends a workout on its face, and the point of a
 * remote is that raising your wrist shows the current set without waiting for a
 * round trip. The Data Layer delivers here whether or not anything is on
 * screen, and [WearWorkoutStore] holds the result for whatever opens next.
 *
 * The alert on `/workout/alert` is here for the same reason and a sharper one:
 * §3 wants the wrist to buzz when a timed set or a rest runs out, and the moment
 * that matters most is the one where nobody is looking at either device. A
 * countdown ticking in a composable cannot fire then, because there is no
 * composition.
 */
@AndroidEntryPoint
class WearStateListenerService : WearableListenerService() {

    @Inject lateinit var store: WearWorkoutStore

    @Inject lateinit var haptics: WearHaptics

    private val json = Json { ignoreUnknownKeys = true }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            when (event.type) {
                DataEvent.TYPE_CHANGED -> store.onDataItem(event.dataItem)

                // The phone deleting the item means the workout is over.
                //
                // **This used to do nothing**, on the reasoning that a terminal
                // phase had already been published so the screen had shown the
                // finish. Half of that is true and the conclusion was not: the
                // wrist then kept showing "Workout finished" until the app was
                // closed and reopened, and an *abandoned* workout — which never
                // publishes a terminal phase at all, it just stops — left the
                // exercise screen up with live-looking buttons. Both seen on
                // hardware.
                //
                // Clearing is what the phone is saying. There is no workout, and
                // §11's answer to that is the absence of a snapshot.
                DataEvent.TYPE_DELETED -> store.onWorkoutGone()

                else -> Unit
            }
        }
    }

    /**
     * A timer reached zero on the phone, so the wrist says so (§3).
     *
     * The watch cannot work this moment out for itself. Both ways out of a rest
     * — it ran out, or the user skipped it — are the same phase change in the
     * snapshot, and only the phone has the events that tell them apart. So this
     * is not a notification of something the watch already knew.
     *
     * The path is checked rather than trusted. The manifest filter is a prefix,
     * matching the phone's own listener, and a service that acted on everything
     * under it would buzz for a snapshot.
     */
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearPaths.ALERT) return

        val message = try {
            json.decodeFromString<WearAlertMessage>(String(event.data))
        } catch (e: Exception) {
            // A newer phone, or a corrupt payload. Silence is the right failure:
            // there is no partial buzz, and nothing on screen depends on this.
            Log.w(TAG, "Unreadable alert from the phone", e)
            return
        }

        if (!message.appliesTo(store.state.value?.sessionId)) {
            Log.d(TAG, "Ignoring an alert for another session")
            return
        }

        Log.d(TAG, "Alerting: ${message.alert}")
        haptics.alert()
    }

    private companion object {
        const val TAG = "WearStateListener"
    }
}
