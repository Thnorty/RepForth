package com.repforth.wear

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.SerializationHelper
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * §3's way back from the watch face, and its removal.
 *
 * A watch spends a workout on its face, so a remote with no return entry is one
 * you get back to through the app launcher — a scroll past every installed app,
 * mid-set. That is the gap; what makes it worth a test is the other half. **A
 * chip left behind after a workout is worse than none**, because it offers a way
 * into a session that no longer exists, and nothing on screen would show it: the
 * app looks right, and the stale chip is on a surface the app never draws.
 *
 * The sharp assertion is the last one. An ordinary ongoing notification is
 * invisible on the watch face; only the [OngoingActivity] decoration promotes it
 * to a chip. Dropping that one call leaves every other assertion here passing and
 * the feature entirely absent.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [SCREENSHOT_SDK])
class WearOngoingActivityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notification = WearWorkoutNotification(context)

    /**
     * Robolectric grants nothing by default, which is the honest starting point:
     * on Android 13 and above this app has no notification permission until the
     * user says yes, and every assertion below about a chip appearing is really
     * an assertion about what happens *after* they do.
     */
    @Before
    fun grantNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `a running workout posts a way back`() {
        notification.update(state(WearPhase.Exercise))

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("front plank", posted.extras.getString("android.title"))
    }

    @Test
    fun `resting still counts as running`() {
        notification.update(state(WearPhase.Rest))

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `a finished workout takes it away`() {
        notification.update(state(WearPhase.Exercise))

        notification.update(state(WearPhase.Finished))

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    /**
     * Abandoning is not finishing anywhere else in this app, and it is not here
     * either — but for once the two do the same thing, and that is the point:
     * both mean no workout is running.
     */
    @Test
    fun `an abandoned workout takes it away too`() {
        notification.update(state(WearPhase.Exercise))

        notification.update(state(WearPhase.Abandoned))

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `no snapshot at all takes it away`() {
        notification.update(state(WearPhase.Exercise))

        notification.update(null)

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    /**
     * One chip, replaced — never a second one beside it.
     *
     * A snapshot arrives several times a minute. A new id per update would stack
     * a notification per set, and the removal above would then take away exactly
     * one of them.
     */
    @Test
    fun `updating replaces the chip rather than stacking another`() {
        notification.update(state(WearPhase.Exercise))
        notification.update(state(WearPhase.Exercise, name = "Barbell Squat"))

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("Barbell Squat", posted.extras.getString("android.title"))
    }

    /** A way back is not an alert; §3's alert is the haptic, with its own switch. */
    @Test
    fun `the chip is silent and ongoing`() {
        notification.update(state(WearPhase.Exercise))

        val posted = shadowOf(manager).allNotifications.single()
        assertTrue("It must not be swipeable away mid-workout", posted.flags and
            android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            "The phone already beeps for the moments that deserve it",
            NotificationManager.IMPORTANCE_LOW,
            manager.getNotificationChannel("workout")?.importance,
        )
    }

    /**
     * The one that would notice the feature being absent.
     *
     * Without the [OngoingActivity] decoration this is an ordinary ongoing
     * notification — correct, silent, tappable in the notification stream, and
     * **invisible on the watch face**, which is the only surface §3 cares about.
     * Every other assertion in this class passes without it.
     */
    @Test
    fun `it is an ongoing activity and not merely a notification`() {
        notification.update(state(WearPhase.Exercise))

        val posted = shadowOf(manager).allNotifications.single()
        assertTrue(
            "Nothing promotes this to the watch face without the OngoingActivity",
            SerializationHelper.hasOngoingActivity(posted),
        )

        val chip: OngoingActivity? = SerializationHelper.create(posted)
        assertNotNull(chip)
        assertNotNull("And it needs somewhere to send the tap", chip!!.touchIntent)
        assertEquals(
            "The exercise name is what the watch face itself shows",
            "front plank",
            chip.status?.getText(context, System.currentTimeMillis())?.toString(),
        )
    }

    /**
     * A refused permission is survived, and produces nothing.
     *
     * `notify` does not throw without it — it silently does nothing — so the
     * only wrong behaviour available here is to believe the chip is there. This
     * is also why the class grants the permission in `@Before`: without that,
     * every assertion above would pass for the wrong reason on Android 13 and
     * up, finding no notification because none was ever allowed.
     */
    @Test
    fun `a refused permission produces no chip and no crash`() {
        shadowOf(context as Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notification.update(state(WearPhase.Exercise))

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    /** The other way to say no, and the only one that exists below Android 13. */
    @Test
    fun `notifications switched off in settings produce no chip either`() {
        shadowOf(manager).setNotificationsEnabled(false)

        notification.update(state(WearPhase.Exercise))

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    /** And the removal still works, so a refusal cannot strand an old chip. */
    @Test
    fun `a workout that ends while notifications are off leaves nothing`() {
        notification.update(state(WearPhase.Exercise))
        shadowOf(manager).setNotificationsEnabled(false)

        notification.update(null)

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertNull(shadowOf(manager).getNotification(1))
    }

    private fun state(phase: WearPhase, name: String = "front plank") = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = phase,
        exerciseId = "0025",
        exerciseName = name,
        setNumber = 1,
        totalSets = 3,
        targetReps = 12,
        targetDurationMs = null,
        restDeadlineElapsedRealtimeMs = null,
        setDeadlineElapsedRealtimeMs = null,
        nextExerciseName = null,
    )
}
