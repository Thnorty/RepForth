package com.repforth.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives the workout snapshot the phone publishes (§11).
 *
 * A service rather than a listener registered by the screen, because the app is
 * usually not open: a watch spends a workout on its face, and the point of a
 * remote is that raising your wrist shows the current set without waiting for a
 * round trip. The Data Layer delivers here whether or not anything is on
 * screen, and [WearWorkoutStore] holds the result for whatever opens next.
 */
@AndroidEntryPoint
class WearStateListenerService : WearableListenerService() {

    @Inject lateinit var store: WearWorkoutStore

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            when (event.type) {
                DataEvent.TYPE_CHANGED -> store.onDataItem(event.dataItem)

                // The phone deleting the item means the workout is over and it
                // said so by removing the state rather than publishing an empty
                // one. Nothing to do here: the phone publishes a terminal phase
                // first, so the screen has already shown the finish.
                DataEvent.TYPE_DELETED -> Unit

                else -> Unit
            }
        }
    }
}
