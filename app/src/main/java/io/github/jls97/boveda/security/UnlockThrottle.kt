package io.github.jls97.boveda.security

import android.annotation.SuppressLint
import android.content.Context

/**
 * Slows down password guessing on the phone: after 5 failed attempts every further failure blocks
 * unlocking for an increasing time (30 s, 1 min, 2 min... up to 16 min). Each attempt also costs
 * a full Argon2id run.
 */
// commit() on purpose: a failed attempt must be on disk before the app can be killed.
@SuppressLint("ApplySharedPref")
internal class UnlockThrottle(context: Context) {
    private val prefs = context.getSharedPreferences("unlock_throttle", Context.MODE_PRIVATE)

    /** Epoch millis until which unlocking is blocked, or 0 if it is allowed now. */
    fun blockedUntil(): Long {
        val until = prefs.getLong(KEY_BLOCKED_UNTIL, 0L)
        return if (until > System.currentTimeMillis()) until else 0L
    }

    /** Records a wrong password and returns the new [blockedUntil] value. */
    fun recordFailure(): Long {
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val blockedUntil = if (failures >= FREE_ATTEMPTS) {
            val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
            System.currentTimeMillis() + (BASE_DELAY_MS shl doublings)
        } else {
            0L
        }
        prefs.edit()
            .putInt(KEY_FAILURES, failures)
            .putLong(KEY_BLOCKED_UNTIL, blockedUntil)
            .commit()
        return blockedUntil
    }

    fun reset() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY_FAILURES = "failures"
        const val KEY_BLOCKED_UNTIL = "blocked_until"
        const val FREE_ATTEMPTS = 5
        const val MAX_DOUBLINGS = 5
        const val BASE_DELAY_MS = 30_000L
    }
}
