package com.repforth.feature.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.media.download.DEFAULT_MEDIA_VERSION
import com.repforth.core.media.download.MediaDownloader
import com.repforth.core.media.download.THUMBNAIL_MEDIA_TYPE
import com.repforth.core.media.manifest.MediaManifestRepository
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.wearprotocol.WearAlert
import com.repforth.core.workout.SessionCommand
import com.repforth.core.workout.SessionEvent
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps a running workout alive while the app is not on screen (§10).
 *
 * §10 asks for an ongoing notification with pause/resume and return-to-workout,
 * and for a foreground service *only* where background-execution rules require
 * one for a user-visible session — so this exists for exactly as long as a
 * workout does and stops itself the moment the session reaches a terminal phase.
 *
 * Without it the rest countdown simply stops when the screen goes away, which
 * is the gap this closes.
 *
 * **On the service type.** Android 14 and above require a declared type whose
 * prerequisites the system checks at `startForeground`. `health` is the obvious
 * reading of "workout", but it demands one of `BODY_SENSORS`,
 * `ACTIVITY_RECOGNITION` or `HIGH_SAMPLING_RATE_SENSORS` — sensitive
 * permissions for hardware this app deliberately does not touch (§3 lists
 * heart-rate and Health Connect as non-goals). Asking for a sensor permission to
 * run a rest timer would be claiming a capability the app does not have.
 * `specialUse` describes it honestly and asks for nothing extra; the cost is
 * that a Play submission would have to justify it, which is noted in
 * docs/PLAN.md rather than discovered at release.
 */
@AndroidEntryPoint
class WorkoutService : Service() {

    @Inject lateinit var controller: SessionController

    @Inject lateinit var exercises: ExerciseRepository

    @Inject lateinit var bridge: WearBridge

    @Inject lateinit var preferences: UserPreferencesDataSource

    /**
     * §11's thumbnail comes from the same cache the session screen fills.
     *
     * `download` is cache-first, so this is a file read for anything the screen
     * has already prefetched and a 6 KB fetch otherwise — and it honours the
     * Wi-Fi-only preference either way, which is how that setting reaches the
     * watch at all.
     */
    @Inject lateinit var media: MediaDownloader

    /**
     * Only for §6's attribution, which must be shown wherever the imagery is.
     *
     * Read from the manifest rather than written into the watch app, because it
     * is upstream's required wording and the watch cannot read the file that
     * defines it.
     */
    @Inject lateinit var manifest: MediaManifestRepository

    private val scope = CoroutineScope(SupervisorJob())
    private var ticker: Job? = null
    private var summaries: Map<String, ExerciseSummary> = emptyMap()
    private var names: Map<String, String> = emptyMap()

    /**
     * The workout's thumbnails, by exercise id, once they are bytes.
     *
     * Concurrent because two coroutines touch it: the state collector reads it
     * on every publish, and [warmThumbnails] writes it from its own job. A
     * missing key means "no picture", which is the correct thing to publish
     * whether the reason is "not fetched yet", "not in the manifest" or "the
     * user restricted downloads to Wi-Fi" — the watch draws its icon for all
     * three, exactly as the phone does.
     */
    private val thumbnails = ConcurrentHashMap<String, ByteArray>()

    /** §6's notice, read once and sent with every thumbnail. */
    private var attribution: String? = null

    /**
     * Whether the last rest ran out rather than being skipped.
     *
     * Held so the notification can say so. It is cleared by the next set,
     * because "Rest is over" stops being true the moment the next one starts
     * and a notification that still says it is lying about the present.
     */
    private var restJustEnded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        scope.launch {
            controller.restore()
            controller.state.collect { snapshot ->
                if (snapshot == null || snapshot.phase.isTerminal) {
                    // §10: do not keep a service alive when no workout is
                    // active. Stopping here rather than waiting to be told
                    // means the only way to leak one is to never reach a
                    // terminal phase, which the state machine forbids.
                    stopSelf()
                } else {
                    if (names.isEmpty()) {
                        summaries = resolveSummaries(snapshot)
                        names = summaries.mapValues { (_, summary) -> summary.name }
                        warmThumbnails(snapshot)
                    }
                    notify(snapshot)
                    // §11: the watch mirrors this service exactly. It is alive
                    // for the life of a workout and dead outside one, which is
                    // precisely the window in which a wrist has anything to
                    // show -- so the snapshot goes out from here rather than
                    // from a second collector with its own lifetime to get
                    // wrong.
                    bridge.publish(
                        snapshot,
                        names,
                        thumbnails[snapshot.currentExerciseId()],
                        attribution,
                    )
                }
            }
        }

        // Rest ending was completely silent: a chronometer counting to zero on
        // a low-importance channel, and then nothing. `session_rest_over` had
        // been written and translated for this and was referenced nowhere.
        //
        // Announced from the service rather than the screen because that is the
        // situation it is for -- the phone face down on a bench, or in a
        // pocket, with the screen off. A composable is not running then.
        scope.launch {
            controller.events.collect { event ->
                when (event) {
                    // Skipped rest is the user's own tap. They know.
                    is SessionEvent.RestEnded -> if (!event.skipped) {
                        restJustEnded = true
                        alert(WearAlert.RestEnded)
                        controller.state.value?.let(::notify)
                    }

                    // A timed set running out is the same moment as a rest
                    // running out, from the same place: the phone is on a bench
                    // and the user is holding a plank, not watching a number.
                    is SessionEvent.TimedSetEnded -> {
                        alert(WearAlert.TimedSetEnded)
                        controller.state.value?.let(::notify)
                    }

                    is SessionEvent.SetRecorded -> restJustEnded = false
                    else -> Unit
                }
            }
        }
    }

    /**
     * A timer reaching zero, felt and heard.
     *
     * Two settings and two senses, read together because they answer the same
     * moment differently: a phone face-down on a bench is felt and not heard, a
     * phone in a bag across the room is heard and not felt. Either may be off,
     * and with both off this does nothing at all -- which is a choice the user
     * made, not a failure.
     *
     * Announced from the service rather than the screen for the same reason the
     * caller gives: this exists for the phone that is not being looked at, and a
     * composable is not running then.
     */
    private suspend fun alert(kind: WearAlert) {
        val preferences = preferences.preferences.first()
        if (preferences.hapticsEnabled) {
            vibrate()
            // The wrist, which §3 asks for by name and which cannot work this
            // moment out for itself: both ways out of a rest -- it ran out, or the
            // user skipped it -- are the same phase change in the snapshot, and
            // only this side has the events that tell them apart.
            //
            // Under the same switch as the phone's own buzz, because the watch has
            // no settings of its own to read (§11) and §12 makes haptics
            // optional. "Haptics off" can only be honoured on the wrist by not
            // sending this at all.
            controller.state.value?.let { bridge.alert(it.sessionId, kind) }
        }
        if (preferences.soundEnabled) playTone()
    }

    /**
     * §12's haptic for a timer reaching zero.
     *
     * A one-shot rather than a pattern: this says "look at me", and the
     * notification says the rest of it. `VIBRATE` is a normal permission, so
     * there is nothing to ask the user for.
     */
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getSystemService(this, VibratorManager::class.java)?.defaultVibrator
        } else {
            ContextCompat.getSystemService(this, Vibrator::class.java)
        }
        vibrator?.vibrate(
            VibrationEffect.createOneShot(REST_OVER_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }

    /**
     * The sound, generated rather than shipped.
     *
     * No audio file, which means no asset to license -- §6 keeps media with
     * someone else's provenance out of this repository, and a bundled sound
     * would be one more thing to track for a noise that plays twice a set. The
     * samples come from [Chime], which computes them.
     *
     * **This was a `ToneGenerator` beep and is not any more.** Its tones are
     * telephony signals: a flat sine, held, then cut. That is a fine "attention"
     * noise and it is not a bell, because the part that makes a bell is the
     * decay and `ToneGenerator` has no way to express one. Asked for a ring
     * rather than a beep, the choice was between shipping an audio file and
     * doing the arithmetic; the arithmetic keeps the property the beep was
     * chosen for in the first place.
     *
     * **On the media stream**, via `USAGE_MEDIA`. That is the volume someone
     * training has already set, because it is the one their music is on -- so
     * this lands at a level they chose, next to what they are listening to, and
     * the volume keys adjust it without a trip into Settings. The alarm stream
     * would be louder and would also play through a phone deliberately silenced,
     * which is a decision the user has already made and this has no business
     * overriding.
     *
     * The notification stream was the other candidate and is the wrong one: it
     * is silent on a phone set to vibrate, which is most phones in a gym, and
     * the haptic above already covers that case on its own.
     *
     * Released after it finishes rather than kept: a held [AudioTrack] owns an
     * output buffer for the length of a workout to make a noise twice a set.
     */
    private suspend fun playTone() {
        val samples = Chime.samples
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        // What it is, rather than where it goes: a short
                        // non-musical cue. Usage picks the stream; this tells
                        // the system what kind of thing is on it.
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Chime.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                // The whole sound is known before it starts, so it is handed
                // over once rather than streamed.
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return

        runCatching {
            track.write(samples, 0, samples.size)
            track.play()
            // Long enough to finish; releasing under it cuts the tail off, and
            // the tail is the part that was asked for.
            delay(Chime.DURATION_MS + TONE_RELEASE_GRACE_MS)
        }
        track.release()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> scope.launch {
                controller.dispatch(SessionCommand.Pause(controller.newCommandId()))
            }

            ACTION_RESUME -> scope.launch {
                controller.dispatch(SessionCommand.Resume(controller.newCommandId()))
            }
        }

        // Something has to be posted before the system's timeout, whatever the
        // intent was, or starting the service is itself a crash.
        //
        // Guarded because the failure modes here are all the system's to decide
        // and all of them throw: a foreground-service type whose prerequisites
        // this device disagrees about, notifications denied, or a start that
        // arrived when the app was no longer allowed to make one. None of that
        // is a reason to take a workout down — the session lives in the
        // database, the screen can still run it, and what is lost is the
        // countdown continuing in the background. Losing a feature beats losing
        // the sets someone is part way through.
        val posted = runCatching {
            startForeground(NOTIFICATION_ID, buildNotification(controller.state.value))
        }
        if (posted.isFailure) {
            stopSelf()
            return START_NOT_STICKY
        }
        startTicking()

        // Not sticky: a workout that the system killed should not be silently
        // resurrected into a notification the user did not ask for. The session
        // is in the database either way, and the screen restores it.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Ends the rest while nothing is watching the screen.
     *
     * Coarser than the screen's tick — a second is the resolution a countdown is
     * read at, and this runs when the display is off.
     */
    private fun startTicking() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                controller.onTick()
                delay(TICK_MS)
            }
        }
    }

    private suspend fun resolveSummaries(
        snapshot: SessionSnapshot,
    ): Map<String, ExerciseSummary> =
        exercises.summaries(snapshot.exercises.map { it.exerciseId })
            .entries.associate { (id, summary) -> id.value to summary }

    private fun SessionSnapshot.currentExerciseId(): String? =
        currentExercise?.exerciseId?.value

    /**
     * Fetches the workout's thumbnails once, in the background.
     *
     * Ahead of time rather than on arrival, because on arrival is too late: a
     * publish that waits for a download shows the wrist the previous exercise
     * until it finishes, and a publish that does not wait shows no picture until
     * something else happens to change the state — which during a set can be a
     * minute away.
     *
     * So the whole plan is warmed at the start, and the one case that cannot be
     * warmed in advance — the first exercise, which the wrist is already looking
     * at — republishes as soon as its bytes land. That is at most one extra
     * publish per workout rather than one per exercise, and the Data Layer drops
     * it entirely if nothing changed.
     *
     * Failures are silent by design. §15 keeps the phone workout working whatever
     * the watch is doing, and a missing picture is not a reason to interrupt a
     * set.
     */
    private fun warmThumbnails(snapshot: SessionSnapshot) {
        scope.launch {
            attribution = manifest.getManifest()?.attribution
            snapshot.exercises.forEach { planned ->
                val id = planned.exerciseId.value
                if (thumbnails.containsKey(id)) return@forEach
                val bytes = loadThumbnail(id) ?: return@forEach
                thumbnails[id] = bytes

                // Only if the wrist is still on this exercise. By the time a
                // download finishes the user may have moved on, and republishing
                // an old snapshot is exactly what the revision check exists to
                // refuse -- better not to send it.
                val current = controller.state.value
                if (current != null && !current.phase.isTerminal &&
                    current.currentExerciseId() == id
                ) {
                    bridge.publish(current, names, bytes, attribution)
                }
            }
        }
    }

    private suspend fun loadThumbnail(exerciseId: String): ByteArray? {
        val ref = summaries[exerciseId]?.thumbnail ?: return null
        if (!ref.isAvailable) return null
        val file = media.download(
            mediaVersion = DEFAULT_MEDIA_VERSION,
            exerciseId = exerciseId,
            mediaType = THUMBNAIL_MEDIA_TYPE,
            mediaRef = ref,
        ).getOrNull() ?: return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    private fun notify(snapshot: SessionSnapshot) {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun buildNotification(snapshot: SessionSnapshot?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // A notification's small icon is drawn as a silhouette: the system
            // keeps the alpha and throws the colour away. The dumbbell is
            // already a single monochrome path, so it survives that; a launcher
            // icon would arrive as a white blob.
            .setSmallIcon(com.repforth.core.designsystem.R.drawable.rf_sym_fitness_center)
            .setContentTitle(snapshot?.let { names[it.currentExercise?.exerciseId?.value] }
                ?: getString(R.string.session_title))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(returnIntent())

        if (snapshot == null) return builder.build()

        builder.setContentText(
            if (restJustEnded && snapshot.phase == SessionPhase.ACTIVE) {
                // The one thing worth reading from across the room. "Set 2 of
                // 4" is true and was already true a minute ago.
                getString(R.string.session_rest_over)
            } else {
                getString(
                    R.string.session_set_of,
                    snapshot.currentSetIndex + 1,
                    snapshot.currentExercise?.target?.sets ?: 0,
                )
            },
        )

        // Whichever clock is running is the one shown, and there is never more
        // than one: a rest and a timed set are different phases.
        //
        // Running or paused, but not the same way. A chronometer is a deadline
        // the system counts towards, so it cannot be stopped — leaving it on
        // while paused would show a timer ticking down during a pause, which is
        // worse than showing none. A paused remainder is written out instead,
        // frozen at whatever is left.
        val now = SystemClock.elapsedRealtime()
        val rest = snapshot.restRemaining(now)
        val set = snapshot.setRemaining(now)
        val remaining = rest ?: set
        when {
            (snapshot.phase == SessionPhase.RESTING || snapshot.phase == SessionPhase.ACTIVE) &&
                remaining != null -> {
                builder.setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(System.currentTimeMillis() + remaining)
                    .setShowWhen(true)
            }

            snapshot.phase == SessionPhase.PAUSED && remaining != null -> {
                builder.setUsesChronometer(false)
                    .setShowWhen(false)
                    .setSubText(
                        getString(
                            if (rest != null) R.string.session_paused_rest else R.string.session_paused_set,
                            remaining.asClock(),
                        ),
                    )
            }

            else -> builder.setShowWhen(false)
        }

        if (snapshot.phase == SessionPhase.PAUSED) {
            builder.addAction(0, getString(R.string.session_resume), action(ACTION_RESUME))
        } else {
            builder.addAction(0, getString(R.string.session_pause), action(ACTION_PAUSE))
        }

        return builder.build()
    }

    /** Milliseconds as m:ss, the way a rest timer is read. */
    private fun Long.asClock(): String {
        val totalSeconds = (this / 1000L).coerceAtLeast(0)
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this,
        name.hashCode(),
        Intent(this, WorkoutService::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Reopens the app on the workout (§10's return-to-workout).
     *
     * Resolved by intent rather than by naming the activity, because the
     * activity lives in `:app` and a feature module must not depend upward.
     */
    private fun returnIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.session_channel_name),
            // Low: it must be visible and it must not interrupt. This
            // notification is a status line for something the user is already
            // doing, and buzzing between sets is the opposite of useful.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.session_channel_description)
            setShowBadge(false)
        }
        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "repforth.workout"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_PAUSE = "com.repforth.session.PAUSE"
        private const val ACTION_RESUME = "com.repforth.session.RESUME"
        private const val TICK_MS = 1_000L

        /** Long enough to feel through a pocket, short enough not to nag. */
        private const val REST_OVER_VIBRATION_MS = 400L

        /**
         * Slack after the chime's own length, before the track is released.
         *
         * The loudness constant that used to sit here went with the
         * `ToneGenerator`: the level is now the media volume the user has set,
         * and the length is `Chime.DURATION_MS`.
         */
        private const val TONE_RELEASE_GRACE_MS = 100L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WorkoutService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
