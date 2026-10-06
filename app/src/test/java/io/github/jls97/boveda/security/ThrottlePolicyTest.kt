package io.github.jls97.boveda.security

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ThrottlePolicyTest {

    private val policy = ThrottlePolicy()

    @Test
    fun defaultsMatchTheDocumentedControl() {
        assertEquals(5, policy.freeAttempts)
        assertEquals(30_000L, policy.baseDelayMs)
        assertEquals(7, policy.maxDoublings)
        // 30 s · 2^7 = 3840 s = 64 min.
        assertEquals(64L * 60_000L, policy.maxDelayMs)
    }

    @Test
    fun firstFourFailuresAreFree() {
        for (failures in 0..4) {
            assertEquals("fallo $failures", 0L, policy.delayAfter(failures))
        }
    }

    @Test
    fun fifthFailureBlocksThirtySecondsAndThenDoubles() {
        assertEquals(30_000L, policy.delayAfter(5))
        assertEquals(60_000L, policy.delayAfter(6))
        assertEquals(120_000L, policy.delayAfter(7))
        assertEquals(240_000L, policy.delayAfter(8))
        assertEquals(480_000L, policy.delayAfter(9))
        assertEquals(960_000L, policy.delayAfter(10))
        assertEquals(1_920_000L, policy.delayAfter(11))
    }

    @Test
    fun delayIsCappedAtSixtyFourMinutes() {
        val cap = 64L * 60_000L
        assertEquals(cap, policy.delayAfter(12))
        assertEquals(cap, policy.delayAfter(13))
        assertEquals(cap, policy.delayAfter(100))
        assertEquals(cap, policy.delayAfter(Int.MAX_VALUE))
    }

    @Test
    fun customParametersAreHonoured() {
        val custom = ThrottlePolicy(freeAttempts = 3, baseDelayMs = 1_000L, maxDoublings = 2)
        assertEquals(0L, custom.delayAfter(2))
        assertEquals(1_000L, custom.delayAfter(3))
        assertEquals(2_000L, custom.delayAfter(4))
        assertEquals(4_000L, custom.delayAfter(5))
        assertEquals(4_000L, custom.delayAfter(50))
        assertEquals(4_000L, custom.maxDelayMs)
    }

    @Test
    fun rejectsNonsensicalParameters() {
        for (build in listOf<() -> ThrottlePolicy>(
            { ThrottlePolicy(freeAttempts = 0) },
            { ThrottlePolicy(baseDelayMs = 0L) },
            { ThrottlePolicy(maxDoublings = -1) },
            { ThrottlePolicy(maxDoublings = 41) },
        )) {
            try {
                build()
                fail("debería rechazar el parámetro")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }
}
