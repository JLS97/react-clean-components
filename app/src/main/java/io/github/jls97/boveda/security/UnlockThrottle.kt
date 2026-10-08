package io.github.jls97.boveda.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

/**
 * Slows down password guessing on the phone: after 5 failed attempts every further failure blocks
 * the next password check for an increasing time (30 s, 1 min, 2 min... up to 64 min). Each
 * attempt also costs a full Argon2id run.
 *
 * The pending time is served with the monotonic clock ([ThrottleClock.elapsedRealtime]), which
 * nobody can move from the settings, and the boot count tells a reboot apart from a clock jump:
 * after a reboot the full penalty is armed again, once. The wall clock only gives the UI an
 * instant for its countdown and, as a second reference, can lengthen a block but never shorten
 * it. Moving the date forward therefore never unblocks.
 *
 * The algorithm is pure Kotlin over a [ThrottleStore] and a [ThrottleClock] so it is unit-tested;
 * the constructor with a [Context] wires SharedPreferences and the Android clocks.
 */
internal class UnlockThrottle(
    private val store: ThrottleStore,
    private val clock: ThrottleClock,
    private val policy: ThrottlePolicy = ThrottlePolicy(),
) {
    constructor(context: Context) : this(PrefsStore(context, AndroidClock(context)), AndroidClock(context))

    /** Epoch millis until which a password check is blocked, or 0 if it is allowed now. */
    fun blockedUntil(): Long {
        var state = store.load()
        if (state.penaltyMs <= 0L) return 0L
        val nowElapsed = clock.elapsedRealtime()
        val nowWall = clock.wallClock()
        val bootCount = clock.bootCount()
        // A reboot restarts elapsedRealtime, so the saved deadline means nothing: the whole
        // penalty is pending again, counted from now. Same when the boot count cannot tell but
        // the monotonic clock went back further than the penalty itself allows.
        val rebooted = bootCount != state.bootCount ||
            nowElapsed < state.blockedElapsedUntil - state.penaltyMs
        if (rebooted) {
            state = state.copy(
                blockedElapsedUntil = nowElapsed + state.penaltyMs,
                bootCount = bootCount,
                blockedWallUntil = nowWall + state.penaltyMs,
            )
            store.save(state)
        }
        // The wall clock may only add blocking, never remove it. If it says more is pending than
        // the monotonic clock, that extra is adopted as a monotonic deadline, once and capped at
        // one full penalty: a date moved back years cannot lock the owner out for years, and a
        // date that stands still cannot keep the block alive forever.
        val remainingWall = (state.blockedWallUntil - nowWall).coerceAtMost(state.penaltyMs)
        if (remainingWall > 0L && remainingWall > state.blockedElapsedUntil - nowElapsed) {
            state = state.copy(blockedElapsedUntil = nowElapsed + remainingWall, blockedWallUntil = 0L)
            store.save(state)
        }
        val remaining = state.blockedElapsedUntil - nowElapsed
        if (remaining <= 0L) {
            // Served in full: remember it so a later reboot does not arm it again.
            store.save(state.copy(penaltyMs = 0L, blockedElapsedUntil = 0L, blockedWallUntil = 0L))
            return 0L
        }
        return nowWall + remaining
    }

    /** Records a wrong password and returns the new [blockedUntil] value. */
    fun recordFailure(): Long {
        val previous = store.load()
        val failures = if (previous.failures == Int.MAX_VALUE) previous.failures else previous.failures + 1
        val penalty = policy.delayAfter(failures)
        val nowWall = clock.wallClock()
        val state = if (penalty > 0L) {
            ThrottleState(
                failures = failures,
                penaltyMs = penalty,
                blockedElapsedUntil = clock.elapsedRealtime() + penalty,
                bootCount = clock.bootCount(),
                blockedWallUntil = nowWall + penalty,
            )
        } else {
            ThrottleState(failures = failures)
        }
        store.save(state)
        return if (penalty > 0L) nowWall + penalty else 0L
    }

    fun reset() {
        store.clear()
    }

    /**
     * Keeps the current state aside: a forced restore replaces a vault without proving anything
     * about its owner, so the count it had must not be cleared by an unlock of the restored copy.
     */
    fun keepForUndo() {
        store.saveKept(store.load())
    }

    /**
     * Puts back the state kept by [keepForUndo], if any, when the vault it protects comes back
     * with an undo, and empties the slot. Never relaxes: with nothing kept the live state stays.
     */
    fun restoreKept() {
        store.loadKept()?.let { store.save(it) }
        store.saveKept(null)
    }

    /** Empties the slot: the vault that state belonged to is gone for good. */
    fun discardKept() {
        store.saveKept(null)
    }

    companion object {
        /**
         * State to adopt from the preferences the previous throttle wrote (`failures` plus
         * `blocked_until`, an epoch instant) when the app is updated with a block pending (R02-5):
         * the penalty the policy gives for [failures], served from now with the monotonic clock
         * for whatever the old deadline has left (at most one full penalty, so a date moved
         * forward cannot lengthen it), and the old deadline kept as the wall-clock reference. A
         * deadline already past leaves only the failure count, like a served penalty.
         */
        internal fun migrateLegacy(
            failures: Int,
            legacyBlockedUntil: Long,
            clock: ThrottleClock,
            policy: ThrottlePolicy,
        ): ThrottleState {
            val remaining = legacyBlockedUntil - clock.wallClock()
            if (remaining <= 0L) return ThrottleState(failures = failures)
            // The old count always had a penalty behind a pending block; if the policies disagree,
            // the time left is the penalty, capped like any other.
            val penalty = policy.delayAfter(failures).takeIf { it > 0L } ?: remaining.coerceAtMost(policy.maxDelayMs)
            return ThrottleState(
                failures = failures,
                penaltyMs = penalty,
                blockedElapsedUntil = clock.elapsedRealtime() + remaining.coerceAtMost(penalty),
                bootCount = clock.bootCount(),
                blockedWallUntil = legacyBlockedUntil,
            )
        }
    }

    // commit() on purpose: a failed attempt must be on disk before the app can be killed.
    @SuppressLint("ApplySharedPref")
    private class PrefsStore(
        context: Context,
        private val clock: ThrottleClock,
        private val policy: ThrottlePolicy = ThrottlePolicy(),
    ) : ThrottleStore {
        private val prefs = context.getSharedPreferences("unlock_throttle", Context.MODE_PRIVATE)

        /** Its own file, so [clear] (every reset) cannot wipe the state kept aside. */
        private val keptPrefs = context.getSharedPreferences("unlock_throttle_kept", Context.MODE_PRIVATE)

        override fun load(): ThrottleState {
            val state = read(prefs)
            // Written by the previous version and never by this one: migrate it once (R02-5). The
            // write below also removes the old key, so this runs a single time.
            if (prefs.contains(KEY_PENALTY_MS) || !prefs.contains(KEY_LEGACY_BLOCKED_UNTIL)) return state
            val migrated = migrateLegacy(state.failures, prefs.getLong(KEY_LEGACY_BLOCKED_UNTIL, 0L), clock, policy)
            write(prefs, migrated)
            return migrated
        }

        override fun save(state: ThrottleState) = write(prefs, state)

        override fun clear() {
            prefs.edit().clear().commit()
        }

        override fun loadKept(): ThrottleState? = if (keptPrefs.contains(KEY_FAILURES)) read(keptPrefs) else null

        override fun saveKept(state: ThrottleState?) {
            if (state == null) keptPrefs.edit().clear().commit() else write(keptPrefs, state)
        }

        private fun read(from: SharedPreferences): ThrottleState = ThrottleState(
            failures = from.getInt(KEY_FAILURES, 0),
            penaltyMs = from.getLong(KEY_PENALTY_MS, 0L),
            blockedElapsedUntil = from.getLong(KEY_BLOCKED_ELAPSED_UNTIL, 0L),
            bootCount = from.getInt(KEY_BOOT_COUNT, 0),
            blockedWallUntil = from.getLong(KEY_BLOCKED_WALL_UNTIL, 0L),
        )

        private fun write(to: SharedPreferences, state: ThrottleState) {
            to.edit()
                .putInt(KEY_FAILURES, state.failures)
                .putLong(KEY_PENALTY_MS, state.penaltyMs)
                .putLong(KEY_BLOCKED_ELAPSED_UNTIL, state.blockedElapsedUntil)
                .putInt(KEY_BOOT_COUNT, state.bootCount)
                .putLong(KEY_BLOCKED_WALL_UNTIL, state.blockedWallUntil)
                .remove(KEY_LEGACY_BLOCKED_UNTIL)
                .commit()
        }

        private companion object {
            const val KEY_FAILURES = "failures"

            /** Deadline (epoch millis) of the throttle before R02-5; only read to migrate it. */
            const val KEY_LEGACY_BLOCKED_UNTIL = "blocked_until"
            const val KEY_PENALTY_MS = "penalty_ms"
            const val KEY_BLOCKED_ELAPSED_UNTIL = "blocked_elapsed_until"
            const val KEY_BOOT_COUNT = "boot_count"
            const val KEY_BLOCKED_WALL_UNTIL = "blocked_wall_until"
        }
    }

    private class AndroidClock(context: Context) : ThrottleClock {
        private val resolver = context.applicationContext.contentResolver

        override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

        override fun wallClock(): Long = System.currentTimeMillis()

        override fun bootCount(): Int =
            try {
                Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, UNKNOWN_BOOT_COUNT)
            } catch (e: Exception) {
                UNKNOWN_BOOT_COUNT
            }

        private companion object {
            const val UNKNOWN_BOOT_COUNT = -1
        }
    }
}
