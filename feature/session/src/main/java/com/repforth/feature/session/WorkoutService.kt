package com.repforth.feature.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.workout.SessionCommand
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    private val scope = CoroutineScope(SupervisorJob())
    private var ticker: Job? = null
    private var names: Map<String, String> = emptyMap()

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
                    if (names.isEmpty()) names = resolveNames(snapshot)
                    notify(snapshot)
                }
            }
        }
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
                controller.onRestTick()
                delay(TICK_MS)
            }
        }
    }

    private suspend fun resolveNames(snapshot: SessionSnapshot): Map<String, String> =
        exercises.summaries(snapshot.exercises.map { it.exerciseId })
            .entries.associate { (id, summary) -> id.value to summary.name }

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
            .setSmallIcon(com.repforth.core.designsystem.R.drawable.rf_ic_exercises)
            .setContentTitle(snapshot?.let { names[it.currentExercise?.exerciseId?.value] }
                ?: getString(R.string.session_title))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(returnIntent())

        if (snapshot == null) return builder.build()

        builder.setContentText(
            getString(
                R.string.session_set_of,
                snapshot.currentSetIndex + 1,
                snapshot.currentExercise?.target?.sets ?: 0,
            ),
        )

        // Rest is shown whether it is running or paused, but not the same way.
        // A chronometer is a deadline the system counts towards, so it cannot be
        // stopped — leaving it on while paused would show a timer ticking down
        // during a pause, which is worse than showing none. Paused rest is
        // written out instead, frozen at whatever is left.
        val remaining = snapshot.restRemaining(SystemClock.elapsedRealtime())
        when {
            snapshot.phase == SessionPhase.RESTING && remaining != null -> {
                builder.setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(System.currentTimeMillis() + remaining)
                    .setShowWhen(true)
            }

            snapshot.phase == SessionPhase.PAUSED && remaining != null -> {
                builder.setUsesChronometer(false)
                    .setShowWhen(false)
                    .setSubText(
                        getString(R.string.session_paused_rest, remaining.asClock()),
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
