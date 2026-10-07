package io.github.jls97.boveda.core.autofill

import io.github.jls97.boveda.core.vault.VaultEntry

/**
 * Texts the autofill screens show about the app or site asking to be filled or saved, decided
 * without Android so they can be tested. Nothing coming from the other app (the domain it
 * claims) is ever interpolated in a sentence: it goes on a line of its own, see [claimedAddress],
 * so it can't close a quote and pass the rest of the warning off as its own.
 */
object FillWarnings {
    const val UNENCRYPTED =
        "Página sin cifrar: la dirección de arriba se abre por http, no https, así que cualquiera en " +
            "la red podría estar sirviendo este formulario."
    const val BROWSER_WITHOUT_DOMAIN =
        "El navegador no ha indicado qué web muestra, así que un vínculo a él alcanzaría " +
            "cualquier página sin dirección que abra."
    const val UNUSUAL_ADDRESS = "La dirección de esta página no es un dominio web normal."
    const val NOT_A_BROWSER =
        "Esta app muestra una página web pero no es un navegador reconocido, " +
            "así que Contraseñora no se fía de esa dirección."
    const val UNVERIFIED_SIGNATURE = "No se ha podido verificar la firma de esta app."
    const val NO_LINKED_WEB = "No hay ninguna entrada vinculada a esta web. Comprueba bien la dirección antes de elegir."
    const val NO_LINKED_APP =
        "No hay ninguna entrada vinculada a esta app. Una app falsa podría imitar a la de tu banco: " +
            "comprueba que es la que esperas antes de elegir."
    const val CHOOSE_WITH_CARE = "Elige solo si sabes qué app es; no se podrá vincular."
    const val SAVED_UNLINKED = "Se guardará sin vincular: tendrás que elegirla a mano al rellenar."
    const val UNREADABLE_ADDRESS = "(dirección ilegible)"

    /**
     * Something wrong with the page itself, shown whatever entries match: a form served without
     * encryption (anyone on the network could be serving it), or a trusted browser that didn't say
     * which page it shows. A linked entry (an app with a WebView, a link saved to a browser by an
     * older version) must not silence it.
     */
    fun anomaly(target: AutofillTarget): String? = when {
        target.unencrypted -> UNENCRYPTED
        target.trustedBrowser && target.host == null && target.claimedWebDomain == null -> BROWSER_WITHOUT_DOMAIN
        else -> null
    }

    /**
     * The address the app claims to show, for a line of its own in monospace, or null when there
     * is none. Already sanitized by TargetResolver; one left empty by that is named as such.
     */
    fun claimedAddress(target: AutofillTarget): String? = target.claimedWebDomain?.ifBlank { UNREADABLE_ADDRESS }

    /** Why the target can't be linked to an entry, beyond what [anomaly] already says; null if nothing else. */
    fun unlinkableReason(target: AutofillTarget): String? {
        val claimed = target.claimedWebDomain
        return when {
            claimed != null && target.trustedBrowser -> if (target.unencrypted) null else UNUSUAL_ADDRESS
            claimed != null -> NOT_A_BROWSER
            target.certificates == null -> UNVERIFIED_SIGNATURE
            else -> null
        }
    }

    /** Shown when no entry is linked to the app or site asking to be filled. */
    fun fillWarning(target: AutofillTarget): String {
        val reason = unlinkableReason(target)
        return when {
            reason != null -> "$reason $CHOOSE_WITH_CARE"
            target.key == null -> CHOOSE_WITH_CARE
            target.host != null -> NO_LINKED_WEB
            else -> NO_LINKED_APP
        }
    }

    /**
     * Shown when the app asking has the package name of an app linked to [entries] but another
     * signature: Android allows one signer per package name, so this is almost certainly a fake.
     */
    fun impersonationWarning(entries: List<VaultEntry>): String {
        val titles = entries.joinToString(", ") { "«${it.title.ifBlank { "(sin nombre)" }}»" }
        return "Esta app tiene el mismo nombre que la vinculada a $titles pero OTRA firma digital: " +
            "probablemente es falsa. No se podrá vincular."
    }

    /**
     * Warnings of the fill screen, in reading order: the page's [anomaly] first, always; then the
     * impersonation warning, or, with nothing linked to the target, [fillWarning].
     */
    fun forFill(target: AutofillTarget, exactMatches: List<VaultEntry>, impersonated: List<VaultEntry>): List<String> =
        listOfNotNull(
            anomaly(target),
            when {
                impersonated.isNotEmpty() -> impersonationWarning(impersonated)
                exactMatches.isEmpty() -> fillWarning(target)
                else -> null
            },
        )

    /** Warnings of the save screen: the page's [anomaly], then why the new entry won't be linked, if it won't. */
    fun forSave(target: AutofillTarget, impersonated: List<VaultEntry>): List<String> {
        val reason = unlinkableReason(target)
        return listOfNotNull(
            anomaly(target),
            when {
                impersonated.isNotEmpty() -> "${impersonationWarning(impersonated)} Se guardará sin vincular."
                reason != null -> "$reason $SAVED_UNLINKED"
                target.key == null -> SAVED_UNLINKED
                else -> null
            },
        )
    }
}
