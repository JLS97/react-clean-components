package io.github.jls97.boveda.core.autofill

import io.github.jls97.boveda.core.vault.VaultEntry

object Domains {
    /**
     * Host of a URL or bare domain, lowercase and without `www.`: `https://www.Banco.es/login`
     * and `banco.es` both give `banco.es`. Returns null for anything that isn't a domain.
     */
    fun host(value: String): String? {
        val authority = value.trim().lowercase()
            .substringAfter("://")
            .takeWhile { it != '/' && it != '?' && it != '#' }
            .substringAfterLast('@')
        val host = authority.substringBefore(':').trimEnd('.').removePrefix("www.")
        val valid = host.contains('.') &&
            !host.startsWith('.') &&
            host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        return if (valid) host else null
    }

    /** `banco.es` covers `banco.es` and its subdomains, such as `online.banco.es`, never `otrobanco.es`. */
    fun covers(savedHost: String, requestHost: String): Boolean =
        requestHost == savedHost || requestHost.endsWith(".$savedHost")
}

/** The app, or the web site inside a browser, that asked to be filled. */
data class AutofillTarget(val packageName: String, val webDomain: String?) {
    val host: String? get() = webDomain?.let { Domains.host(it) }

    /** How the target is remembered in [VaultEntry.autofillTargets]. */
    val key: String get() = host?.let { CredentialMatcher.WEB_PREFIX + it } ?: CredentialMatcher.APP_PREFIX + packageName

    /** Short text shown to the user. */
    val label: String get() = host ?: packageName
}

/**
 * Finds the entries that belong to an app or web site. Exact matches come from the entry's web
 * address or from targets remembered earlier; they are the only ones offered first, so an app
 * that merely pretends to be your bank is never matched automatically.
 */
object CredentialMatcher {
    const val WEB_PREFIX = "web:"
    const val APP_PREFIX = "android:"

    private val GENERIC_WORDS = setOf(
        "com", "www", "net", "org", "app", "apps", "android", "mobile", "movil", "online", "login", "the",
        "and", "mi", "my", "cuenta", "account", "web", "es", "co", "io",
    )

    fun isExactMatch(entry: VaultEntry, target: AutofillTarget): Boolean {
        val host = target.host
        return if (host != null) {
            Domains.host(entry.url)?.let { Domains.covers(it, host) } == true ||
                entry.autofillTargets.any { it.startsWith(WEB_PREFIX) && Domains.covers(it.removePrefix(WEB_PREFIX), host) }
        } else {
            APP_PREFIX + target.packageName in entry.autofillTargets
        }
    }

    fun exactMatches(entries: List<VaultEntry>, target: AutofillTarget): List<VaultEntry> =
        entries.filter { isExactMatch(it, target) }.sortedBy { it.title.lowercase() }

    /** Entries whose name looks related (for example "Instagram" for `com.instagram.android`). */
    fun suggestions(entries: List<VaultEntry>, target: AutofillTarget): List<VaultEntry> {
        val parts = (target.host ?: target.packageName)
            .split('.', '-', '_')
            .map { it.lowercase() }
            .filter { it.length >= 3 && it !in GENERIC_WORDS }
        if (parts.isEmpty()) return emptyList()
        return entries
            .filter { entry ->
                !isExactMatch(entry, target) &&
                    words(entry.title).any { word ->
                        parts.any { part -> part.contains(word) || (part.length >= 5 && word.contains(part)) }
                    }
            }
            .sortedBy { it.title.lowercase() }
    }

    /** Name proposed for a new entry: the domain, or the meaningful part of a package name. */
    fun suggestedTitle(target: AutofillTarget): String =
        target.host
            ?: target.packageName.split('.')
                .lastOrNull { it.length >= 3 && it.lowercase() !in GENERIC_WORDS }
                ?.replaceFirstChar { it.uppercase() }
            ?: target.packageName

    /** The entry, remembering [target] so it is an exact match next time. */
    fun remember(entry: VaultEntry, target: AutofillTarget): VaultEntry =
        if (isExactMatch(entry, target)) entry else entry.copy(autofillTargets = entry.autofillTargets + target.key)

    private fun words(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 && it !in GENERIC_WORDS }
}
