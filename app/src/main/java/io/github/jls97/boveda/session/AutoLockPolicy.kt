package io.github.jls97.boveda.session

/**
 * Pure decision of the auto-lock: when does the vault lock on its own? No Android, so it can be
 * tested on the JVM. Every instant is a reading of the monotonic clock (`elapsedRealtime`), which
 * keeps running while the process is frozen or the phone sleeps, so a comparison against it
 * catches the time the process could not count.
 *
 * Three rules, combined:
 * - Inactivity: `autoLockSeconds` without the user touching the app. With the "lock when leaving
 *   the app" setting (`autoLockSeconds == 0`) there is no inactivity timer, except while a system
 *   screen opened on purpose (file picker, autofill settings) is excused from the leaving rule:
 *   then [EXTERNAL_ACTIVITY_GRACE_MS] applies as inactivity timeout, so that mode never leaves the
 *   vault open without limit.
 * - Leaving the app: with `autoLockSeconds == 0` the vault locks when the app goes to the
 *   background, unless an external screen was announced and the announcement is still fresh
 *   ([EXTERNAL_ACTIVITY_GRACE_MS]). An announcement never outlives its deadline, so a picker
 *   abandoned with the Home button cannot keep the exception alive.
 * - Coming back: locks if the time in the background exceeded `autoLockSeconds` (whatever the
 *   last interaction says) and, with any setting, if it exceeded [MAX_BACKGROUND_MS].
 */
object AutoLockPolicy {
    /** How long an announced system screen excuses the "lock when leaving" rule, and the inactivity timeout meanwhile. */
    const val EXTERNAL_ACTIVITY_GRACE_MS = 90_000L

    /** Hard cap: after this long in the background the vault locks on return, with any setting. */
    const val MAX_BACKGROUND_MS = 5 * 60_000L

    /** Deadline of an announcement made at [nowMs]. */
    fun externalActivityDeadline(nowMs: Long): Long = nowMs + EXTERNAL_ACTIVITY_GRACE_MS

    /** True while an announcement with deadline [expectedUntilMs] (0 when none) is still fresh. */
    fun isExternalActivityExpected(expectedUntilMs: Long, nowMs: Long): Boolean =
        expectedUntilMs > 0L && nowMs < expectedUntilMs

    /** Inactivity timeout in effect, in millis, or 0 when there is none. */
    fun inactivityTimeoutMs(autoLockSeconds: Int, externalActivityExpected: Boolean): Long = when {
        autoLockSeconds > 0 -> autoLockSeconds * 1_000L
        externalActivityExpected -> EXTERNAL_ACTIVITY_GRACE_MS
        else -> 0L
    }

    /** True once [nowMs] is at least the inactivity timeout past [lastInteractionMs]. */
    fun shouldLockForInactivity(
        autoLockSeconds: Int,
        externalActivityExpected: Boolean,
        lastInteractionMs: Long,
        nowMs: Long,
    ): Boolean {
        val timeout = inactivityTimeoutMs(autoLockSeconds, externalActivityExpected)
        return timeout > 0L && nowMs - lastInteractionMs >= timeout
    }

    /** Whether to lock the moment the app goes to the background. */
    fun shouldLockOnBackground(autoLockSeconds: Int, externalActivityExpected: Boolean): Boolean =
        autoLockSeconds == 0 && !externalActivityExpected

    /**
     * Whether to lock when the app comes back to the front. [backgroundSinceMs] is when it went
     * to the background, or `null` if it never did (first start, or an activity swap that had no
     * background in between). The time in the background counts on its own, whatever
     * [lastInteractionMs] says, and over [MAX_BACKGROUND_MS] it locks with any setting.
     */
    fun shouldLockOnForeground(
        autoLockSeconds: Int,
        backgroundSinceMs: Long?,
        lastInteractionMs: Long,
        nowMs: Long,
    ): Boolean {
        val timeout = autoLockSeconds * 1_000L
        if (timeout > 0L && nowMs - lastInteractionMs >= timeout) return true
        if (backgroundSinceMs == null) return false
        val inBackground = nowMs - backgroundSinceMs
        if (timeout > 0L && inBackground >= timeout) return true
        return inBackground >= MAX_BACKGROUND_MS
    }
}
