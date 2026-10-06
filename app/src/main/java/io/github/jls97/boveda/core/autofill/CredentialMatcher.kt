package io.github.jls97.boveda.core.autofill

import io.github.jls97.boveda.core.vault.VaultEntry
import java.net.IDN

object Domains {
    /**
     * Host of a URL or bare domain, lowercase, in ASCII and without `www.`: `https://www.Banco.es/login`
     * and `banco.es` both give `banco.es`. Internationalized names are converted to punycode
     * (`bаnco.es` with a Cyrillic `а` becomes `xn--bnco-….es`), so a look-alike never reads as the
     * real domain and both spellings compare equal. Returns null for anything that isn't a domain.
     */
    fun host(value: String): String? {
        val authority = value.trim().lowercase()
            .substringAfter("://")
            .takeWhile { it != '/' && it != '?' && it != '#' }
            .substringAfterLast('@')
        val raw = authority.substringBefore(':').trimEnd('.').removePrefix("www.")
        val host = if (raw.all { it.code < 128 }) {
            raw
        } else {
            runCatching { IDN.toASCII(raw, IDN.ALLOW_UNASSIGNED).lowercase() }.getOrNull() ?: return null
        }
        val labels = host.split('.')
        val valid = labels.size >= 2 && labels.all { isValidLabel(it) }
        return if (valid) host else null
    }

    /** DNS label: letters, digits and inner hyphens, 1 to 63 characters. */
    private fun isValidLabel(label: String): Boolean =
        label.length in 1..63 &&
            !label.startsWith('-') &&
            !label.endsWith('-') &&
            label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }

    /** True when some label of [host] is punycode (`xn--`): the real name has non-ASCII characters. */
    fun isIdn(host: String): Boolean = host.split('.').any { it.startsWith(IDN_PREFIX) }

    /**
     * `banco.es` covers `banco.es` and its subdomains, such as `online.banco.es`, never `otrobanco.es`.
     * A saved host that is a public suffix (`github.io`, `blogspot.com`, `co.uk`: anyone can own a
     * name under it) covers nothing but itself, and IP addresses only match exactly. Without the
     * Public Suffix List loaded every subdomain is covered, as before.
     */
    fun covers(savedHost: String, requestHost: String): Boolean {
        if (requestHost == savedHost) return true
        if (!requestHost.endsWith(".$savedHost")) return false
        if (savedHost.all { it in '0'..'9' || it == '.' }) return false
        return !PublicSuffixes.isLoaded || PublicSuffixes.registrableDomain(savedHost) != null
    }

    private const val IDN_PREFIX = "xn--"
}

/**
 * Signing certificates of an app as SHA-256 fingerprints (lowercase hex), read from Android, which
 * verifies them: an app can't fake another app's certificate without its private key. [current]
 * identifies the app today; [accepted] adds older certificates from a key rotation. Apps signed by
 * several keys at once use one token with all of them sorted and joined by commas.
 */
data class AppCertificates(val current: String, val accepted: Set<String>)

/** The app, or the web site inside a trusted browser, that asked to be filled or saved. */
data class AutofillTarget(
    val packageName: String,
    /** Null when Android couldn't give the app's certificates; the app is then never linked. */
    val certificates: AppCertificates?,
    /** Domain reported by a trusted browser: the only web domain ever used to match entries. */
    val webDomain: String? = null,
    /** Domain shown by an app that isn't a trusted browser. Only displayed as a warning. */
    val claimedWebDomain: String? = null,
    /** The browser said the page isn't served over https: never linked, shown as a warning. */
    val unencrypted: Boolean = false,
    /** The app is a trusted browser (verified certificate): without a domain it is never linked. */
    val trustedBrowser: Boolean = false,
) {
    val host: String? get() = webDomain?.let { Domains.host(it) }

    /** The domain has non-ASCII characters, shown in punycode: the screen warns about it. */
    val isIdn: Boolean get() = host?.let { Domains.isIdn(it) } == true

    /**
     * How the target is remembered in [VaultEntry.autofillTargets], or null when it must not be:
     * apps whose certificates are unknown, apps that show web pages without being a trusted
     * browser, and trusted browsers that didn't say which page they show, because a link to them
     * would reach every page they open.
     */
    val key: String?
        get() = when {
            host != null -> CredentialMatcher.WEB_PREFIX + host
            claimedWebDomain != null || certificates == null || trustedBrowser -> null
            else -> CredentialMatcher.APP_PREFIX + packageName + CredentialMatcher.CERTIFICATE_SEPARATOR + certificates.current
        }

    /** Short text shown to the user. */
    val label: String get() = host ?: packageName
}

object TargetResolver {
    /**
     * Decides what a request is about. A web domain is trusted only when a known browser with a
     * matching certificate reports it. Any other app is identified by its package name and
     * certificate, and a domain it reports is kept only to warn about it. A page whose browser
     * reports a [webScheme] other than https is only a claim too: on an unencrypted page anyone on
     * the network could be serving the form. A missing scheme (older browsers) changes nothing.
     */
    fun resolve(
        packageName: String,
        certificates: AppCertificates?,
        reportedWebDomain: String?,
        webScheme: String? = null,
    ): AutofillTarget {
        val reported = reportedWebDomain?.trim()?.takeIf { it.isNotEmpty() }
        val trustedBrowser = certificates != null && TrustedBrowsers.isTrusted(packageName, certificates)
        val unencrypted = reported != null && isUnencrypted(webScheme)
        return if (reported != null && trustedBrowser && !unencrypted && Domains.host(reported) != null) {
            AutofillTarget(packageName, certificates, webDomain = reported, trustedBrowser = true)
        } else {
            AutofillTarget(
                packageName,
                certificates,
                claimedWebDomain = reported?.take(MAX_CLAIM_LENGTH),
                unencrypted = unencrypted,
                trustedBrowser = trustedBrowser,
            )
        }
    }

    /** True only when the browser reported a scheme and it isn't https. */
    fun isUnencrypted(webScheme: String?): Boolean {
        val scheme = webScheme?.trim()?.removeSuffix(":")?.lowercase().orEmpty()
        return scheme.isNotEmpty() && scheme != "https"
    }

    private const val MAX_CLAIM_LENGTH = 100
}

/**
 * Finds the entries that belong to an app or web site. Exact matches come from the entry's web
 * address (for web sites in trusted browsers) or from links remembered earlier, which for apps
 * include the signing certificate. Only exact matches are offered without a warning.
 */
object CredentialMatcher {
    const val WEB_PREFIX = "web:"
    const val APP_PREFIX = "android:"
    const val CERTIFICATE_SEPARATOR = "@"

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
            entry.autofillTargets.any { appLinkMatches(it, target) }
        }
    }

    /** `android:<package>@<certificate>` matches when both the package and the certificate do. */
    private fun appLinkMatches(link: String, target: AutofillTarget): Boolean {
        if (!link.startsWith(APP_PREFIX)) return false
        val accepted = target.certificates?.accepted ?: return false
        val body = link.removePrefix(APP_PREFIX)
        val packageName = body.substringBefore(CERTIFICATE_SEPARATOR)
        val certificate = body.substringAfter(CERTIFICATE_SEPARATOR, missingDelimiterValue = "")
        return packageName == target.packageName && certificate.isNotEmpty() && certificate in accepted
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

    /**
     * The entry, remembering [target] so it is an exact match next time. Targets that must not be
     * remembered (see [AutofillTarget.key]) leave the entry unchanged.
     */
    fun remember(entry: VaultEntry, target: AutofillTarget): VaultEntry {
        val key = target.key ?: return entry
        return if (isExactMatch(entry, target)) entry else entry.copy(autofillTargets = entry.autofillTargets + key)
    }

    private fun words(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 && it !in GENERIC_WORDS }
}
