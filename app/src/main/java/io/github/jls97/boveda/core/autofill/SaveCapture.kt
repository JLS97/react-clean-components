package io.github.jls97.boveda.core.autofill

import io.github.jls97.boveda.core.vault.VaultEntry

/**
 * Decisions of the "Guardar en Bóveda" flow that need no Android: which typed password is worth
 * keeping, whether an existing entry is proposed for update and what that update would change.
 */
object SaveCapture {

    /**
     * The password to save from a submitted form. A form with new-password fields is a sign-up or
     * a password change: the new password is the one the server knows from now on, so it wins over
     * the current-password field even when both are filled. Two new-password fields that disagree
     * (a mistyped confirmation) yield null: saving either one would lock the user out.
     */
    fun choosePassword(current: String?, newPasswords: List<String?>): String? {
        val typed = newPasswords.filterNotNull().filter { it.isNotEmpty() }
        if (typed.isNotEmpty()) return typed.first().takeIf { first -> typed.all { it == first } }
        return current?.takeIf { it.isNotEmpty() }
    }

    /**
     * The entry whose password would be replaced by default, or null to propose a new entry:
     * only an exact match of the destination ([matches]) whose user is the one typed. Without a
     * typed user (a password change form rarely has one) nothing is preselected. For a web site
     * ([targetHost] not null) the entry must also be anchored to that very host: a page on
     * `aviso.banco.es` (an abandoned or hijacked subdomain) still matches an entry of `banco.es`,
     * but it doesn't get to propose overwriting its password.
     */
    fun preselect(matches: List<VaultEntry>, typedUsername: String, targetHost: String? = null): VaultEntry? {
        val typed = typedUsername.trim()
        if (typed.isEmpty()) return null
        return matches.firstOrNull { entry ->
            entry.username.trim().equals(typed, ignoreCase = true) && (targetHost == null || anchoredTo(entry, targetHost))
        }
    }

    /** True when the entry's address or one of its web links names exactly [host]. */
    private fun anchoredTo(entry: VaultEntry, host: String): Boolean =
        Domains.host(entry.url) == host ||
            entry.autofillTargets.any { it.startsWith(CredentialMatcher.WEB_PREFIX) && Domains.host(it.removePrefix(CredentialMatcher.WEB_PREFIX)) == host }

    /**
     * An exact match that already holds the typed password (and the typed user, when there is
     * one): saving would change nothing, so the screen only says so.
     */
    fun alreadyStored(matches: List<VaultEntry>, typedUsername: String, password: String): VaultEntry? {
        val typed = typedUsername.trim()
        return matches.firstOrNull { entry ->
            entry.password == password && (typed.isEmpty() || entry.username.trim().equals(typed, ignoreCase = true))
        }
    }

    /** User to store when updating [existing]: the typed one, or the stored one if the form had none. */
    fun mergedUsername(existing: VaultEntry?, typedUsername: String): String {
        val typed = typedUsername.trim()
        return if (typed.isEmpty() && existing != null) existing.username else typed
    }

    /** What updating [existing] with the typed data changes, for the user to read before confirming. */
    fun changeSummary(existing: VaultEntry, typedUsername: String, password: String): String {
        val username = mergedUsername(existing, typedUsername)
        val userPart = when {
            username == existing.username -> "Usuario: sin cambios"
            existing.username.isEmpty() -> "Usuario: «$username», antes vacío"
            else -> "Usuario: «$username», antes «${existing.username}»"
        }
        val passwordPart = if (password == existing.password) {
            "Contraseña: sin cambios"
        } else {
            "Contraseña: nueva de ${password.length} caracteres, antes ${existing.password.length}"
        }
        return "$userPart · $passwordPart"
    }
}
