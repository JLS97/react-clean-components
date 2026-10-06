package io.github.jls97.boveda.security

import io.github.jls97.boveda.security.ClipboardClearPolicy.ObservedClip
import io.github.jls97.boveda.security.ClipboardClearPolicy.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardClearPolicyTest {

    private val stamp = 1_000L
    private val deadline = 61_000L
    private val ours = ObservedClip(ClipboardClearPolicy.LABEL, stamp)

    @Test
    fun labelIsNeutral() {
        assertEquals("Bóveda", ClipboardClearPolicy.LABEL)
    }

    @Test
    fun ownClipNeedsLabelAndStamp() {
        assertTrue(ClipboardClearPolicy.isOwnClip(ClipboardClearPolicy.LABEL, stamp, stamp))
        assertTrue(ClipboardClearPolicy.isOwnClip(StringBuilder(ClipboardClearPolicy.LABEL), stamp, stamp))
        assertFalse("otra etiqueta", ClipboardClearPolicy.isOwnClip("Contraseña", stamp, stamp))
        assertFalse("sin etiqueta", ClipboardClearPolicy.isOwnClip(null, stamp, stamp))
        assertFalse("sin marca", ClipboardClearPolicy.isOwnClip(ClipboardClearPolicy.LABEL, null, stamp))
        assertFalse("marca de otra copia", ClipboardClearPolicy.isOwnClip(ClipboardClearPolicy.LABEL, stamp + 1, stamp))
    }

    @Test
    fun expiryIsInclusive() {
        assertFalse(ClipboardClearPolicy.hasExpired(deadline, deadline - 1))
        assertTrue(ClipboardClearPolicy.hasExpired(deadline, deadline))
        assertTrue(ClipboardClearPolicy.hasExpired(deadline, deadline + 5_000))
    }

    @Test
    fun ownExpiredClipIsCleared() {
        assertEquals(Verdict.CLEAR, ClipboardClearPolicy.decide(ours, stamp, deadline, deadline, force = false))
        assertEquals(Verdict.CLEAR, ClipboardClearPolicy.decide(ours, stamp, deadline + 60_000, deadline, force = false))
    }

    @Test
    fun ownClipBeforeDeadlineWaitsUnlessForced() {
        assertEquals(Verdict.KEEP_PENDING, ClipboardClearPolicy.decide(ours, stamp, deadline - 1, deadline, force = false))
        assertEquals(Verdict.CLEAR, ClipboardClearPolicy.decide(ours, stamp, deadline - 1, deadline, force = true))
    }

    @Test
    fun foreignClipIsNeverCleared() {
        val foreign = listOf(
            ObservedClip("Nota", null),
            ObservedClip("Nota", stamp),
            ObservedClip(ClipboardClearPolicy.LABEL, null),
            ObservedClip(ClipboardClearPolicy.LABEL, stamp + 1),
            ObservedClip(null, null),
        )
        for (clip in foreign) {
            assertEquals("$clip", Verdict.KEEP_FOREIGN, ClipboardClearPolicy.decide(clip, stamp, deadline + 1, deadline, force = false))
            assertEquals("$clip forzado", Verdict.KEEP_FOREIGN, ClipboardClearPolicy.decide(clip, stamp, deadline - 1, deadline, force = true))
        }
    }

    @Test
    fun hiddenClipCountsAsOurs() {
        // Android hides the clipboard from apps without focus: the secret must still go.
        assertEquals(Verdict.CLEAR, ClipboardClearPolicy.decide(null, stamp, deadline, deadline, force = false))
        assertEquals(Verdict.CLEAR, ClipboardClearPolicy.decide(null, stamp, 0L, deadline, force = true))
        assertEquals(Verdict.KEEP_PENDING, ClipboardClearPolicy.decide(null, stamp, deadline - 1, deadline, force = false))
    }

    @Test
    fun lockedDeviceStillClearsWhenTheAlarmFires() {
        // With the device locked Android hides the description too (only reads are restricted,
        // clearPrimaryClip still works): the alarm receiver must clear, never just wait.
        val hiddenByKeyguard: ObservedClip? = null
        assertEquals(Verdict.CLEAR, ClipboardClearPolicy.decide(hiddenByKeyguard, stamp, deadline, deadline, force = false))
        assertEquals(Verdict.CLEAR, ClipboardClearPolicy.decide(hiddenByKeyguard, stamp, deadline + 9 * 60_000, deadline, force = false))
    }

    @Test
    fun laterCopyReplacesTheEarlierOne() {
        val second = ObservedClip(ClipboardClearPolicy.LABEL, stamp + 10_000)
        // The timer of the first copy finds the second one and leaves it to its own timer.
        assertEquals(Verdict.KEEP_FOREIGN, ClipboardClearPolicy.decide(second, stamp, deadline, deadline, force = false))
        assertEquals(Verdict.CLEAR, ClipboardClearPolicy.decide(second, stamp + 10_000, deadline + 10_000, deadline + 10_000, force = false))
    }
}
