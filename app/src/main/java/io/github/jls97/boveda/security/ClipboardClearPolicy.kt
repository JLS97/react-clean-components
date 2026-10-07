package io.github.jls97.boveda.security

/**
 * Pure decision of the clipboard clearing: is the clip on the clipboard still the one Bóveda put
 * there, and has its time run out? No Android, so it can be tested on the JVM.
 *
 * Every clip Bóveda copies carries the neutral label [LABEL] (it never describes the secret) and
 * a stamp of its own in the description extras ([EXTRA_STAMP]). The clearing paths (in-process
 * timer, lock, and the alarm receiver that survives the death of the process) read the current
 * description and only clear when it is ours, so a clip the user copied afterwards is left alone.
 *
 * Android hides the clipboard from apps without focus and while the device is locked: from the
 * background the description reads as `null` whether the clipboard is empty or holds someone
 * else's clip. Only reads are restricted; clearing always works. Since the clip was put there by
 * us and nothing tells us it changed, a hidden clip is treated as still ours: leaving a secret
 * behind is worse than wiping a clip we cannot see.
 */
object ClipboardClearPolicy {
    /** Neutral clip label: a reader of the clipboard learns nothing about what was copied. */
    const val LABEL = "Contraseñora"

    /** Key of the stamp inside `ClipDescription.extras`. */
    const val EXTRA_STAMP = "io.github.jls97.boveda.CLIP_STAMP"

    /** What a clearing path could read of the current clip; `null` when Android hid it or it is empty. */
    data class ObservedClip(val label: String?, val stamp: Long?)

    enum class Verdict {
        /** The clip is (or may be) ours and its time is up, or clearing was forced: wipe it. */
        CLEAR,

        /** Someone else's clip is on the clipboard: never touch it. */
        KEEP_FOREIGN,

        /** Our clip, but its time has not run out yet. */
        KEEP_PENDING,
    }

    /** True when [label] and [stamp] are those of the clip Bóveda copied with [ownStamp]. */
    fun isOwnClip(label: CharSequence?, stamp: Long?, ownStamp: Long): Boolean =
        label?.toString() == LABEL && stamp != null && stamp == ownStamp

    /** True once the clip copied with deadline [deadlineMs] (monotonic clock) should be gone. */
    fun hasExpired(deadlineMs: Long, nowMs: Long): Boolean = nowMs >= deadlineMs

    /**
     * Decides whether to clear the clipboard. [observed] is what could be read of the current clip
     * (`null` when hidden or empty), [ownStamp] and [deadlineMs] identify the clip Bóveda copied,
     * and [force] clears before the deadline (lock), still never over a foreign clip.
     */
    fun decide(observed: ObservedClip?, ownStamp: Long, nowMs: Long, deadlineMs: Long, force: Boolean): Verdict {
        if (observed != null && !isOwnClip(observed.label, observed.stamp, ownStamp)) return Verdict.KEEP_FOREIGN
        if (!force && !hasExpired(deadlineMs, nowMs)) return Verdict.KEEP_PENDING
        return Verdict.CLEAR
    }
}
