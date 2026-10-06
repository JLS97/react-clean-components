package io.github.jls97.boveda.security

/**
 * Pure policy of the attempt throttle: how long the n-th wrong password blocks the next attempt.
 * No Android, no clocks, no storage, so it can be tested on the JVM.
 *
 * The first [freeAttempts] failures cost nothing; from then on each failure doubles the delay,
 * starting at [baseDelayMs] and capped after [maxDoublings] doublings (30 s · 2^7 = 64 min with
 * the defaults).
 */
class ThrottlePolicy(
    val freeAttempts: Int = 5,
    val baseDelayMs: Long = 30_000L,
    val maxDoublings: Int = 7,
) {
    init {
        require(freeAttempts >= 1) { "freeAttempts debe ser >= 1" }
        require(baseDelayMs > 0) { "baseDelayMs debe ser > 0" }
        require(maxDoublings in 0..40) { "maxDoublings fuera de rango" }
    }

    /** Longest delay this policy ever imposes. */
    val maxDelayMs: Long = baseDelayMs shl maxDoublings

    /** Milliseconds the next attempt must wait after [failures] consecutive wrong passwords. */
    fun delayAfter(failures: Int): Long {
        if (failures < freeAttempts) return 0L
        val doublings = (failures - freeAttempts).coerceIn(0, maxDoublings)
        return baseDelayMs shl doublings
    }
}

/**
 * Clocks the throttle reads. Injected so the blocking algorithm can be tested with fake time.
 *
 * Only [elapsedRealtime] (monotonic, restarts on every boot) is trusted to *serve* a penalty;
 * [wallClock] can be changed from the system settings by whoever holds the phone, so it may only
 * ever lengthen a block, and [bootCount] tells a reboot apart from a clock jump.
 */
interface ThrottleClock {
    /** Milliseconds since boot, including deep sleep. Never goes backwards within a boot. */
    fun elapsedRealtime(): Long

    /** Epoch millis of the adjustable system clock. */
    fun wallClock(): Long

    /** Number of boots of the device, or a constant if it cannot be read. */
    fun bootCount(): Int
}

/** Persisted state of the throttle. [penaltyMs] is 0 when nothing is pending. */
data class ThrottleState(
    val failures: Int = 0,
    val penaltyMs: Long = 0L,
    val blockedElapsedUntil: Long = 0L,
    val bootCount: Int = 0,
    val blockedWallUntil: Long = 0L,
)

/** Where the throttle keeps its state. Every write must be on disk before it returns. */
interface ThrottleStore {
    fun load(): ThrottleState

    fun save(state: ThrottleState)

    fun clear()
}
