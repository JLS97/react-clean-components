package io.github.jls97.boveda.autofill

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.jls97.boveda.core.autofill.AutofillTarget
import java.util.UUID

/**
 * Credentials typed in another app that the user may want to save. `toString` hides them. The
 * password lives in a char array so the store can zero it ([wipe]) when the save is dropped;
 * [password] reads it as text for the screen and is empty once wiped.
 */
internal class PendingSave(
    val target: AutofillTarget,
    val username: String,
    private val secret: CharArray,
) {
    @Volatile
    var wiped: Boolean = false
        private set

    val password: String get() = if (wiped) "" else String(secret)

    fun wipe() {
        wiped = true
        secret.fill('\u0000')
    }

    override fun toString() = "PendingSave(${target.label})"
}

/**
 * Hands credentials from the autofill service to [AutofillActivity] inside this process, so they
 * never travel in an Intent. At most [MAX_SIZE] wait at once (the oldest is dropped), each one
 * expires [MAX_AGE_MS] after [put] (checked on every [put] and [get], and also scheduled through
 * [schedule] so it doesn't wait for the next call), the save screen removes its own as soon as it
 * closes and [clear] drops them all when the screen turns off. Whatever leaves the store is wiped.
 * [clock] and [schedule] are injectable so the expiry can be tested on the JVM.
 */
internal open class PendingSaveStore(
    private val clock: () -> Long,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit = { _, _ -> },
) {
    private class Stored(val save: PendingSave, val createdAt: Long)

    /** Insertion order is age: the first entry is always the oldest. */
    private val saves = LinkedHashMap<String, Stored>()

    @Synchronized
    fun put(save: PendingSave): String {
        prune()
        while (saves.size >= MAX_SIZE) drop(saves.keys.first())
        val token = UUID.randomUUID().toString()
        saves[token] = Stored(save, clock())
        schedule(MAX_AGE_MS) { remove(token) }
        return token
    }

    /** Reading doesn't remove it, so the save screen survives a rotation; see [remove]. */
    @Synchronized
    fun get(token: String): PendingSave? {
        prune()
        return saves[token]?.save
    }

    @Synchronized
    fun remove(token: String) {
        drop(token)
    }

    /** Drops and wipes every pending save: nothing typed elsewhere outlives the screen turning off. */
    @Synchronized
    fun clear() {
        saves.values.forEach { it.save.wipe() }
        saves.clear()
    }

    private fun drop(token: String) {
        saves.remove(token)?.save?.wipe()
    }

    private fun prune() {
        val now = clock()
        val expired = saves.filterValues { now - it.createdAt > MAX_AGE_MS }.keys
        expired.forEach { drop(it) }
    }

    companion object {
        const val MAX_SIZE = 8
        const val MAX_AGE_MS = 5 * 60 * 1_000L
    }
}

internal object PendingSaves : PendingSaveStore(SystemClock::elapsedRealtime, ::postDelayed)

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

private fun postDelayed(delayMs: Long, action: () -> Unit) {
    mainHandler.postDelayed({ action() }, delayMs)
}
