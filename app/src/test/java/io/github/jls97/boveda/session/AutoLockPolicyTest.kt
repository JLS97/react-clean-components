package io.github.jls97.boveda.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockPolicyTest {

    private val now = 1_000_000L
    private val grace = AutoLockPolicy.EXTERNAL_ACTIVITY_GRACE_MS
    private val cap = AutoLockPolicy.MAX_BACKGROUND_MS

    @Test
    fun externalActivityExpiresAfterTheGrace() {
        val until = AutoLockPolicy.externalActivityDeadline(now)
        assertEquals(now + grace, until)
        assertTrue("recién anunciada", AutoLockPolicy.isExternalActivityExpected(until, now))
        assertTrue("un instante antes del plazo", AutoLockPolicy.isExternalActivityExpected(until, until - 1))
        assertFalse("en el plazo", AutoLockPolicy.isExternalActivityExpected(until, until))
        assertFalse("mucho después", AutoLockPolicy.isExternalActivityExpected(until, until + 60 * 60_000L))
        assertFalse("sin anuncio", AutoLockPolicy.isExternalActivityExpected(0L, now))
    }

    @Test
    fun expiredExceptionLocksOnLeavingAgain() {
        val until = AutoLockPolicy.externalActivityDeadline(now)
        // "Al salir de la app": while the picker is expected, leaving does not lock...
        assertFalse(AutoLockPolicy.shouldLockOnBackground(0, AutoLockPolicy.isExternalActivityExpected(until, now + 1_000)))
        // ...once the grace is over, it locks as always.
        assertTrue(AutoLockPolicy.shouldLockOnBackground(0, AutoLockPolicy.isExternalActivityExpected(until, until)))
    }

    @Test
    fun lockWhenLeavingOnlyAppliesToTheZeroSetting() {
        assertTrue(AutoLockPolicy.shouldLockOnBackground(0, externalActivityExpected = false))
        assertFalse(AutoLockPolicy.shouldLockOnBackground(0, externalActivityExpected = true))
        assertFalse(AutoLockPolicy.shouldLockOnBackground(60, externalActivityExpected = false))
        assertFalse(AutoLockPolicy.shouldLockOnBackground(900, externalActivityExpected = true))
    }

    @Test
    fun inactivityTimeoutFollowsTheSetting() {
        assertEquals(60_000L, AutoLockPolicy.inactivityTimeoutMs(60))
        assertEquals(900_000L, AutoLockPolicy.inactivityTimeoutMs(900))
        assertEquals("sin temporizador al salir", 0L, AutoLockPolicy.inactivityTimeoutMs(0))
        // "Lock when leaving" has no inactivity timer at all, whatever was announced.
        assertFalse(AutoLockPolicy.shouldLockForInactivity(0, now, now + 24 * 60 * 60_000L))
    }

    /**
     * One tick of the VaultSession timer with "lock when leaving": the same two rules, composed
     * from the same inputs as in production.
     */
    private fun tickLocks(expectedUntil: Long, backgroundSince: Long?, lastTouch: Long, t: Long): Boolean =
        AutoLockPolicy.shouldLockForInactivity(0, lastTouch, t) ||
            AutoLockPolicy.shouldLockOnAnnouncementExpiry(0, expectedUntil, backgroundSince, t)

    @Test
    fun leavingModeStillLocksWhileThePickerIsAbandoned() {
        // The touch that opens the picker (onUserInteraction) comes a few ms before the
        // announcement, and the app goes to the background a bit later, when the picker shows.
        // The user presses Home from the picker: nobody calls onAppForeground, so the ticks must
        // lock on their own exactly when the announcement expires, and not before.
        val lastTouch = now - 50
        val until = AutoLockPolicy.externalActivityDeadline(now)
        val backgroundSince = now + 200
        var lockedAt: Long? = null
        var t = backgroundSince
        while (t <= until + 5_000 && lockedAt == null) {
            if (tickLocks(until, backgroundSince, lastTouch, t)) lockedAt = t
            t += 1_000
        }
        assertNotNull("un selector abandonado bloquea solo", lockedAt)
        assertTrue("no antes del plazo", lockedAt!! >= until)
        assertTrue("en el primer tick desde el plazo", lockedAt < until + 1_000)
        // Checked at the exact instants too: not with the whole window ahead...
        assertFalse(tickLocks(until, backgroundSince, lastTouch, until - 1))
        // ...and yes at the deadline itself, even with a touch 30 s before the announcement.
        assertTrue(tickLocks(until, backgroundSince, lastTouch, until))
        assertFalse(tickLocks(until, backgroundSince, now - 30_000, until - 1))
        assertTrue(tickLocks(until, backgroundSince, now - 30_000, until))
    }

    @Test
    fun announcementWithoutLeavingNeverLocks() {
        // The picker never opened (or the user is back in front): the app stays in the
        // foreground, so an expired announcement is no reason to lock.
        val until = AutoLockPolicy.externalActivityDeadline(now)
        for (t in now..until + 10 * 60_000L step 1_000) {
            assertFalse("t = $t", tickLocks(until, backgroundSince = null, lastTouch = now, t = t))
        }
    }

    @Test
    fun returningFromThePickerClearsTheAnnouncement() {
        // onAppForeground sets the deadline to 0 and backgroundSince to null: the tick after that
        // has nothing to lock on, however old the announcement was.
        val until = AutoLockPolicy.externalActivityDeadline(now)
        assertFalse(AutoLockPolicy.shouldLockOnForeground(0, now + 200, now, now + 30_000))
        assertFalse(tickLocks(expectedUntil = 0L, backgroundSince = null, lastTouch = now, t = until + 60_000))
        // An announcement alone, without the background, is not enough either way.
        assertFalse(AutoLockPolicy.shouldLockOnAnnouncementExpiry(0, 0L, backgroundSinceMs = now, nowMs = until))
    }

    @Test
    fun announcementExpiryOnlyAppliesToTheZeroSetting() {
        // With an inactivity setting the timer already applies; the deadline adds nothing.
        val until = AutoLockPolicy.externalActivityDeadline(now)
        assertTrue(AutoLockPolicy.shouldLockOnAnnouncementExpiry(0, until, now + 200, until))
        assertFalse(AutoLockPolicy.shouldLockOnAnnouncementExpiry(60, until, now + 200, until))
        assertFalse(AutoLockPolicy.shouldLockOnAnnouncementExpiry(900, until, now + 200, until + 60_000))
    }

    @Test
    fun inactivityCountsRealTimeNotTicks() {
        // A frozen process misses its one-second ticks; the first tick after thawing compares
        // against the monotonic clock and finds the whole gap.
        val lastTouch = now
        assertFalse(AutoLockPolicy.shouldLockForInactivity(60, lastTouch, lastTouch + 59_999))
        assertTrue(AutoLockPolicy.shouldLockForInactivity(60, lastTouch, lastTouch + 60_000))
        assertTrue(AutoLockPolicy.shouldLockForInactivity(60, lastTouch, lastTouch + 3 * 60 * 60_000L))
    }

    @Test
    fun returningAfter61SecondsWithSetting60Locks() {
        val left = now
        assertTrue(AutoLockPolicy.shouldLockOnForeground(60, left, lastInteractionMs = left, nowMs = left + 61_000))
    }

    @Test
    fun returningAfter10SecondsWithSetting60DoesNotLock() {
        val left = now
        assertFalse(AutoLockPolicy.shouldLockOnForeground(60, left, lastInteractionMs = left, nowMs = left + 10_000))
    }

    @Test
    fun timeInBackgroundCountsEvenIfTheInteractionLooksRecent() {
        // Something touched lastInteraction while the app was in the background (an unlock from
        // the autofill screen, for instance): the time away still counts on its own.
        val left = now
        val back = left + 61_000
        assertTrue(AutoLockPolicy.shouldLockOnForeground(60, left, lastInteractionMs = back - 1_000, nowMs = back))
    }

    @Test
    fun inactivityBeforeLeavingAlsoLocksOnReturn() {
        // Idle 50 s in the foreground, then 20 s away: 70 s without touching the app.
        val lastTouch = now
        val left = lastTouch + 50_000
        assertTrue(AutoLockPolicy.shouldLockOnForeground(60, left, lastTouch, left + 20_000))
        assertFalse(AutoLockPolicy.shouldLockOnForeground(60, left, lastTouch, left + 5_000))
    }

    @Test
    fun fiveMinutesInBackgroundLocksWithAnySetting() {
        val left = now
        for (setting in listOf(0, 60, 300, 900)) {
            // Just under the cap: only the setting decides (0, 300 and 900 do not lock yet).
            val justUnder = left + cap - 1
            assertEquals("ajuste $setting, bajo el tope", setting == 60, AutoLockPolicy.shouldLockOnForeground(setting, left, justUnder, justUnder))
            // At the cap: everyone locks, even "lock when leaving" that was excused by a picker.
            val atCap = left + cap
            assertTrue("ajuste $setting, en el tope", AutoLockPolicy.shouldLockOnForeground(setting, left, atCap, atCap))
        }
    }

    @Test
    fun noBackgroundNoCap() {
        // First start or activity swap without a background in between: only inactivity counts.
        assertFalse(AutoLockPolicy.shouldLockOnForeground(0, null, now, now + 2 * cap))
        assertFalse(AutoLockPolicy.shouldLockOnForeground(60, null, now, now + 59_000))
        assertTrue(AutoLockPolicy.shouldLockOnForeground(60, null, now, now + 60_000))
    }
}
