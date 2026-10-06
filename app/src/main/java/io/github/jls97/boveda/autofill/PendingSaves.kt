package io.github.jls97.boveda.autofill

import android.os.SystemClock
import io.github.jls97.boveda.core.autofill.AutofillTarget
import java.util.UUID

/** Credentials typed in another app that the user may want to save. `toString` hides them. */
internal class PendingSave(
    val target: AutofillTarget,
    val username: String,
    val password: String,
) {
    override fun toString() = "PendingSave(${target.label})"
}

/**
 * Hands credentials from the autofill service to [AutofillActivity] inside this process, so they
 * never travel in an Intent. At most [MAX_SIZE] wait at once (the oldest is dropped), each one
 * expires [MAX_AGE_MS] after [put] (checked on every [put] and [get]) and the save screen removes
 * its own as soon as it closes. [clock] is injectable so the expiry can be tested on the JVM.
 */
internal open class PendingSaveStore(private val clock: () -> Long) {
    private class Stored(val save: PendingSave, val createdAt: Long)

    /** Insertion order is age: the first entry is always the oldest. */
    private val saves = LinkedHashMap<String, Stored>()

    @Synchronized
    fun put(save: PendingSave): String {
        prune()
        while (saves.size >= MAX_SIZE) saves.remove(saves.keys.first())
        return UUID.randomUUID().toString().also { saves[it] = Stored(save, clock()) }
    }

    /** Reading doesn't remove it, so the save screen survives a rotation; see [remove]. */
    @Synchronized
    fun get(token: String): PendingSave? {
        prune()
        return saves[token]?.save
    }

    @Synchronized
    fun remove(token: String) {
        saves.remove(token)
    }

    private fun prune() {
        val now = clock()
        saves.entries.removeAll { now - it.value.createdAt > MAX_AGE_MS }
    }

    companion object {
        const val MAX_SIZE = 8
        const val MAX_AGE_MS = 5 * 60 * 1_000L
    }
}

internal object PendingSaves : PendingSaveStore(SystemClock::elapsedRealtime)
