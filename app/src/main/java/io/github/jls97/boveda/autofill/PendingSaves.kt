package io.github.jls97.boveda.autofill

import android.os.SystemClock
import io.github.jls97.boveda.core.autofill.AutofillTarget
import java.util.UUID

/** Credentials typed in another app that the user may want to save. `toString` hides them. */
internal class PendingSave(
    val target: AutofillTarget,
    val username: String,
    val password: String,
    val createdAt: Long = SystemClock.elapsedRealtime(),
) {
    override fun toString() = "PendingSave(${target.label})"
}

/**
 * Hands credentials from the autofill service to [AutofillActivity] inside this process, so they
 * never travel in an Intent. They expire after a few minutes.
 */
internal object PendingSaves {
    private const val MAX_AGE_MS = 5 * 60 * 1_000L
    private val saves = HashMap<String, PendingSave>()

    @Synchronized
    fun put(save: PendingSave): String {
        prune()
        return UUID.randomUUID().toString().also { saves[it] = save }
    }

    /** Reading doesn't remove it, so the save screen survives a rotation; see [remove]. */
    @Synchronized
    fun get(token: String): PendingSave? {
        prune()
        return saves[token]
    }

    @Synchronized
    fun remove(token: String) {
        saves.remove(token)
    }

    private fun prune() {
        val now = SystemClock.elapsedRealtime()
        saves.entries.removeAll { now - it.value.createdAt > MAX_AGE_MS }
    }
}
