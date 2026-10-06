package io.github.jls97.boveda.core.autofill

import java.io.InputStream
import java.net.IDN

/**
 * Public Suffix List (https://publicsuffix.org), ICANN and private sections: the suffixes under
 * which anyone can register a name (`es`, `co.uk`) or publish content (`github.io`, `blogspot.com`).
 * Loaded once from the `public_suffix_list.dat` asset at startup; while it isn't loaded the
 * queries return null and the callers keep their simpler behaviour.
 *
 * Hosts are expected lowercase and in ASCII (punycode), as [Domains.host] returns them.
 */
object PublicSuffixes {
    private class Rules(val exact: Set<String>, val wildcards: Set<String>, val exceptions: Set<String>)

    @Volatile
    private var rules: Rules? = null

    val isLoaded: Boolean get() = rules != null

    /** Reads the list in its official text format (UTF-8, one rule per line, `//` comments). */
    fun load(input: InputStream) {
        input.bufferedReader(Charsets.UTF_8).useLines { load(it) }
    }

    fun load(lines: Sequence<String>) {
        val exact = HashSet<String>()
        val wildcards = HashSet<String>()
        val exceptions = HashSet<String>()
        for (line in lines) {
            val rule = line.substringBefore(' ').trim()
            if (rule.isEmpty() || rule.startsWith("//")) continue
            when {
                rule.startsWith("!") -> ascii(rule.substring(1))?.let(exceptions::add)
                rule.startsWith("*.") -> ascii(rule.substring(2))?.let(wildcards::add)
                else -> ascii(rule)?.let(exact::add)
            }
        }
        rules = Rules(exact, wildcards, exceptions)
    }

    /**
     * The public suffix of [host] (`co.uk` for `shop.example.co.uk`, `github.io` for
     * `user.github.io`), or null when the list isn't loaded. Unknown TLDs count as a public
     * suffix on their own, as the specification says.
     */
    fun publicSuffix(host: String): String? {
        val rules = rules ?: return null
        val labels = host.lowercase().trimEnd('.').split('.')
        if (labels.any { it.isEmpty() }) return null
        var best = 1
        for (index in labels.indices) {
            val candidate = labels.subList(index, labels.size).joinToString(".")
            // An exception rule wins outright and marks the labels after it as the suffix.
            if (candidate in rules.exceptions) return labels.subList(index + 1, labels.size).joinToString(".")
            if (candidate in rules.exact) best = maxOf(best, labels.size - index)
            if (index > 0 && candidate in rules.wildcards) best = maxOf(best, labels.size - index + 1)
        }
        return labels.subList(labels.size - best, labels.size).joinToString(".")
    }

    /** True when [host] itself is a public suffix: nobody owns it as a whole. */
    fun isPublicSuffix(host: String): Boolean = publicSuffix(host) == host.lowercase().trimEnd('.')

    /**
     * The registrable domain (eTLD+1) of [host]: `banco.es` for `online.banco.es`, `user.github.io`
     * for `user.github.io`. Null when the list isn't loaded or [host] is a public suffix itself.
     */
    fun registrableDomain(host: String): String? {
        val normalized = host.lowercase().trimEnd('.')
        val suffix = publicSuffix(normalized) ?: return null
        if (suffix == normalized) return null
        val above = normalized.removeSuffix(".$suffix")
        return above.substringAfterLast('.') + "." + suffix
    }

    /** Rules are written in Unicode in the list; hosts are compared in punycode. */
    private fun ascii(rule: String): String? {
        val lower = rule.lowercase()
        if (lower.all { it.code < 128 }) return lower
        return runCatching { IDN.toASCII(lower, IDN.ALLOW_UNASSIGNED).lowercase() }.getOrNull()
    }
}
