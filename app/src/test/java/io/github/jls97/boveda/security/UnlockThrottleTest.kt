package io.github.jls97.boveda.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blocking algorithm with a fake store and fake clocks: the penalty is served with the
 * monotonic clock, a reboot re-arms it once and the wall clock can never shorten it.
 */
class UnlockThrottleTest {

    private class FakeStore : ThrottleStore {
        var state = ThrottleState()
        var saves = 0
        var kept: ThrottleState? = null

        override fun load(): ThrottleState = state

        override fun save(state: ThrottleState) {
            this.state = state
            saves++
        }

        override fun clear() {
            state = ThrottleState()
        }

        override fun loadKept(): ThrottleState? = kept

        override fun saveKept(state: ThrottleState?) {
            kept = state
        }
    }

    private class FakeClock(
        var elapsed: Long = 1_000_000L,
        var wall: Long = 1_700_000_000_000L,
        var boots: Int = 42,
    ) : ThrottleClock {
        override fun elapsedRealtime(): Long = elapsed

        override fun wallClock(): Long = wall

        override fun bootCount(): Int = boots

        /** Time passes normally: both clocks advance together. */
        fun tick(ms: Long) {
            elapsed += ms
            wall += ms
        }

        /** The phone restarts: the boot count grows and the monotonic clock starts again. */
        fun reboot(uptimeAfterBoot: Long, downtimeMs: Long = 60_000L) {
            boots++
            elapsed = uptimeAfterBoot
            wall += downtimeMs + uptimeAfterBoot
        }
    }

    private val store = FakeStore()
    private val clock = FakeClock()
    private val throttle = UnlockThrottle(store, clock)

    private fun fail(times: Int): Long {
        var until = 0L
        repeat(times) { until = throttle.recordFailure() }
        return until
    }

    private fun remaining(): Long {
        val until = throttle.blockedUntil()
        return if (until == 0L) 0L else until - clock.wall
    }

    // (a) five failures block 30 s

    @Test
    fun fourFailuresDoNotBlock() {
        assertEquals(0L, fail(4))
        assertEquals(0L, throttle.blockedUntil())
        assertEquals(4, store.state.failures)
        assertEquals(0L, store.state.penaltyMs)
    }

    @Test
    fun fifthFailureBlocksThirtySeconds() {
        val until = fail(5)
        assertEquals(clock.wall + 30_000L, until)
        assertEquals(until, throttle.blockedUntil())
        assertEquals(30_000L, remaining())
        with(store.state) {
            assertEquals(5, failures)
            assertEquals(30_000L, penaltyMs)
            assertEquals(clock.elapsed + 30_000L, blockedElapsedUntil)
            assertEquals(clock.boots, bootCount)
            assertEquals(clock.wall + 30_000L, blockedWallUntil)
        }
    }

    @Test
    fun everyFurtherFailureDoublesThePenalty() {
        fail(5)
        clock.tick(30_000L)
        assertEquals(0L, throttle.blockedUntil())
        assertEquals(60_000L, throttle.recordFailure() - clock.wall)
        clock.tick(60_000L)
        assertEquals(0L, throttle.blockedUntil())
        assertEquals(120_000L, throttle.recordFailure() - clock.wall)
    }

    @Test
    fun failuresPersistAcrossAttemptsAndResetClearsEverything() {
        fail(5)
        throttle.reset()
        assertEquals(ThrottleState(), store.state)
        assertEquals(0L, throttle.blockedUntil())
        // Back to square one: four free attempts again.
        assertEquals(0L, fail(4))
    }

    // (b) moving the wall clock forward does not unblock

    @Test
    fun advancingTheDateOneYearDoesNotUnblock() {
        fail(5)
        clock.wall += 365L * 24 * 60 * 60 * 1000
        assertEquals(30_000L, remaining())
        // Only a second of real time went by: 29 s still pending, reported on the new date.
        clock.elapsed += 1_000L
        assertEquals(29_000L, remaining())
        assertEquals(clock.wall + 29_000L, throttle.blockedUntil())
    }

    @Test
    fun advancingTheDateAfterEveryFailureDoesNotUnblockEither() {
        fail(5)
        repeat(3) {
            clock.wall += 24L * 60 * 60 * 1000
            assertTrue(throttle.blockedUntil() > 0L)
        }
        assertEquals(30_000L, remaining())
    }

    @Test
    fun settingTheDateBackLengthensTheBlockAtMostOneFullPenalty() {
        fail(5)
        clock.elapsed += 30_000L // served by the monotonic clock
        clock.wall -= 365L * 24 * 60 * 60 * 1000
        // The wall clock may add blocking (it says 1 year is left) but at most one full penalty,
        // served monotonically: the owner is never locked out for a year by a date moved back.
        assertEquals(30_000L, remaining())
        clock.elapsed += 29_000L
        assertEquals(1_000L, remaining())
        clock.elapsed += 1_000L
        assertEquals(0L, throttle.blockedUntil())
        clock.wall += 365L * 24 * 60 * 60 * 1000
        assertEquals(0L, throttle.blockedUntil())
    }

    // (c) advancing the monotonic clock does unblock

    @Test
    fun advancingElapsedRealtimeUnblocksExactlyAtExpiry() {
        fail(5)
        clock.tick(29_999L)
        assertEquals(1L, remaining())
        clock.tick(1L)
        assertEquals(0L, throttle.blockedUntil())
        // Served in full: the pending penalty is cleared but the failure count is kept.
        assertEquals(0L, store.state.penaltyMs)
        assertEquals(5, store.state.failures)
        assertEquals(60_000L, throttle.recordFailure() - clock.wall)
    }

    @Test
    fun aDateThatStandsStillAddsAtMostOnePenaltyAndThenElapsedRealtimeAloneServesIt() {
        fail(5)
        // Only the monotonic clock moves: the wall clock still says 30 s are pending, so that is
        // adopted once...
        clock.elapsed += 30_000L
        assertEquals(30_000L, remaining())
        assertEquals(0L, store.state.blockedWallUntil)
        // ...and served by elapsedRealtime alone, whatever the date does.
        clock.elapsed += 15_000L
        assertEquals(15_000L, remaining())
        clock.elapsed += 15_000L
        assertEquals(0L, throttle.blockedUntil())
        clock.elapsed += 60_000L
        assertEquals(0L, throttle.blockedUntil())
    }

    // (d) a reboot re-arms the full penalty, once

    @Test
    fun rebootRearmsTheFullPenaltyFromTheNewElapsedRealtime() {
        fail(5)
        clock.tick(20_000L) // 10 s left
        clock.reboot(uptimeAfterBoot = 5_000L)
        assertEquals(30_000L, remaining())
        assertEquals(clock.boots, store.state.bootCount)
        assertEquals(5_000L + 30_000L, store.state.blockedElapsedUntil)
        // Only once: the next check keeps counting from the re-armed deadline.
        clock.tick(10_000L)
        assertEquals(20_000L, remaining())
        clock.tick(20_000L)
        assertEquals(0L, throttle.blockedUntil())
    }

    @Test
    fun rebootDoesNotRearmAPenaltyAlreadyServed() {
        fail(5)
        clock.tick(30_000L)
        assertEquals(0L, throttle.blockedUntil())
        clock.reboot(uptimeAfterBoot = 5_000L)
        assertEquals(0L, throttle.blockedUntil())
    }

    @Test
    fun rebootWithTheDateMovedForwardStillRearms() {
        fail(5)
        clock.reboot(uptimeAfterBoot = 1_000L, downtimeMs = 365L * 24 * 60 * 60 * 1000)
        assertEquals(30_000L, remaining())
    }

    @Test
    fun monotonicClockGoingBackIsTreatedAsARebootWhenBootCountCannotTell() {
        fail(5)
        clock.elapsed = 100L // same boot count reported, but elapsedRealtime restarted
        assertEquals(30_000L, remaining())
        assertEquals(100L + 30_000L, store.state.blockedElapsedUntil)
    }

    // (e) 64 minute cap

    @Test
    fun penaltyIsCappedAtSixtyFourMinutes() {
        val cap = 64L * 60_000L
        fail(12)
        assertEquals(cap, remaining())
        clock.tick(cap)
        assertEquals(0L, throttle.blockedUntil())
        assertEquals(cap, throttle.recordFailure() - clock.wall)
        assertEquals(cap, store.state.penaltyMs)
    }

    // (f) a forced restore keeps the count aside for the vault that may come back (R01-9)

    @Test
    fun stateKeptForUndoSurvivesAResetAndComesBackWithTheUndo() {
        fail(5)
        clock.tick(30_000L) // served: the count stays
        assertEquals(0L, throttle.blockedUntil())
        // Forced restore of another vault, then its owner unlocks it: the live state is cleared...
        throttle.keepForUndo()
        throttle.reset()
        assertEquals(ThrottleState(), store.state)
        assertEquals(0L, fail(4))
        // ...but undoing the restore brings the original vault back with its own count: the next
        // wrong password is the sixth, not the first.
        throttle.restoreKept()
        assertEquals(5, store.state.failures)
        assertNull(store.kept)
        assertEquals(60_000L, throttle.recordFailure() - clock.wall)
    }

    @Test
    fun aPendingBlockKeptForUndoIsStillServedAfterTheUndo() {
        fail(5)
        throttle.keepForUndo()
        throttle.reset()
        clock.tick(10_000L)
        throttle.restoreKept()
        assertEquals(20_000L, remaining())
    }

    @Test
    fun restoringWithNothingKeptChangesNothingAndDiscardEmptiesTheSlot() {
        fail(3)
        throttle.restoreKept()
        assertEquals(3, store.state.failures)
        throttle.keepForUndo()
        throttle.discardKept()
        assertNull(store.kept)
        throttle.reset()
        throttle.restoreKept()
        assertEquals(ThrottleState(), store.state)
    }

    @Test
    fun customPolicyIsUsed() {
        val custom = UnlockThrottle(store, clock, ThrottlePolicy(freeAttempts = 2, baseDelayMs = 1_000L, maxDoublings = 1))
        assertEquals(0L, custom.recordFailure())
        assertEquals(1_000L, custom.recordFailure() - clock.wall)
        assertEquals(2_000L, custom.recordFailure() - clock.wall)
        assertEquals(2_000L, custom.recordFailure() - clock.wall)
    }
}
