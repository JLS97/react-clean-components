package io.github.jls97.boveda.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(60_000L, AutoLockPolicy.inactivityTimeoutMs(60, externalActivityExpected = false))
        assertEquals(60_000L, AutoLockPolicy.inactivityTimeoutMs(60, externalActivityExpected = true))
        assertEquals(900_000L, AutoLockPolicy.inactivityTimeoutMs(900, externalActivityExpected = false))
        assertEquals("sin temporizador al salir", 0L, AutoLockPolicy.inactivityTimeoutMs(0, externalActivityExpected = false))
        assertEquals("tope mientras dura la excepción", grace, AutoLockPolicy.inactivityTimeoutMs(0, externalActivityExpected = true))
    }

    @Test
    fun leavingModeStillLocksWhileThePickerIsAbandoned() {
        // The user opened the picker (exception active) and pressed Home: nobody calls
        // onAppForeground, so the inactivity timer must lock on its own after the grace.
        val lastTouch = now
        assertFalse(AutoLockPolicy.shouldLockForInactivity(0, true, lastTouch, lastTouch + grace - 1))
        assertTrue(AutoLockPolicy.shouldLockForInactivity(0, true, lastTouch, lastTouch + grace))
        // Without the exception, "lock when leaving" has no inactivity timer at all.
        assertFalse(AutoLockPolicy.shouldLockForInactivity(0, false, lastTouch, lastTouch + 24 * 60 * 60_000L))
    }

    @Test
    fun inactivityCountsRealTimeNotTicks() {
        // A frozen process misses its one-second ticks; the first tick after thawing compares
        // against the monotonic clock and finds the whole gap.
        val lastTouch = now
        assertFalse(AutoLockPolicy.shouldLockForInactivity(60, false, lastTouch, lastTouch + 59_999))
        assertTrue(AutoLockPolicy.shouldLockForInactivity(60, false, lastTouch, lastTouch + 60_000))
        assertTrue(AutoLockPolicy.shouldLockForInactivity(60, false, lastTouch, lastTouch + 3 * 60 * 60_000L))
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
