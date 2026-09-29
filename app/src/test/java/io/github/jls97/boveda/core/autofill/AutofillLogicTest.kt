package io.github.jls97.boveda.core.autofill

import io.github.jls97.boveda.core.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillLogicTest {

    private fun kind(
        hints: List<String> = emptyList(),
        input: InputKind = InputKind.OTHER,
        tag: String? = null,
        attributes: Map<String, String> = emptyMap(),
        texts: List<String> = emptyList(),
    ) = FieldClassifier.classify(FieldSignals(hints, input, tag, attributes, texts))

    @Test
    fun declaredHintsWin() {
        assertEquals(FieldKind.PASSWORD, kind(hints = listOf("password"), input = InputKind.TEXT))
        assertEquals(FieldKind.PASSWORD, kind(hints = listOf("current-password")))
        assertEquals(FieldKind.NEW_PASSWORD, kind(hints = listOf("newPassword"), input = InputKind.PASSWORD))
        assertEquals(FieldKind.USERNAME, kind(hints = listOf("emailAddress")))
        assertEquals(FieldKind.USERNAME, kind(hints = listOf("username"), input = InputKind.TEXT))
        assertEquals(FieldKind.IGNORED, kind(hints = listOf("smsOTPCode"), input = InputKind.TEXT))
        // "off" says nothing, so the keyboard type decides.
        assertEquals(FieldKind.PASSWORD, kind(hints = listOf("off"), input = InputKind.PASSWORD))
    }

    @Test
    fun readsHtmlFromBrowsers() {
        assertEquals(FieldKind.PASSWORD, kind(tag = "input", attributes = mapOf("type" to "password")))
        assertEquals(
            FieldKind.NEW_PASSWORD,
            kind(tag = "INPUT", attributes = mapOf("type" to "password", "autocomplete" to "new-password")),
        )
        assertEquals(FieldKind.USERNAME, kind(tag = "input", attributes = mapOf("type" to "email")))
        assertEquals(FieldKind.USERNAME, kind(tag = "input", attributes = mapOf("autocomplete" to "username")))
        assertEquals(FieldKind.IGNORED, kind(tag = "input", attributes = mapOf("type" to "hidden")))
        assertEquals(FieldKind.IGNORED, kind(tag = "input", attributes = mapOf("type" to "search")))
        assertEquals(
            FieldKind.USERNAME,
            kind(input = InputKind.TEXT, tag = "input", attributes = mapOf("type" to "text", "name" to "usuario")),
        )
        // A web text input without keyboard type or telling name is still a text field.
        assertEquals(FieldKind.OTHER_TEXT, kind(tag = "input", attributes = mapOf("type" to "text", "name" to "fld1")))
        assertEquals(FieldKind.OTHER_TEXT, kind(tag = "input", attributes = mapOf("name" to "fld1")))
    }

    @Test
    fun fallsBackToKeyboardTypeAndWords() {
        assertEquals(FieldKind.PASSWORD, kind(input = InputKind.PASSWORD))
        assertEquals(FieldKind.NEW_PASSWORD, kind(input = InputKind.PASSWORD, texts = listOf("Repite la contraseña")))
        assertEquals(FieldKind.USERNAME, kind(input = InputKind.EMAIL))
        assertEquals(FieldKind.USERNAME, kind(input = InputKind.TEXT, texts = listOf("et_user_name")))
        assertEquals(FieldKind.USERNAME, kind(input = InputKind.TEXT, texts = listOf("userName")))
        assertEquals(FieldKind.USERNAME, kind(input = InputKind.TEXT, texts = listOf("DNI o NIE")))
        assertEquals(FieldKind.USERNAME, kind(input = InputKind.OTHER, texts = listOf("Teléfono")))
        assertEquals(FieldKind.PASSWORD, kind(input = InputKind.TEXT, texts = listOf("PIN")))
        assertEquals(FieldKind.PASSWORD, kind(input = InputKind.TEXT, texts = listOf("passwordInput")))
        assertEquals(FieldKind.OTHER_TEXT, kind(input = InputKind.TEXT, texts = listOf("Nombre")))
        assertEquals(FieldKind.OTHER_TEXT, kind(input = InputKind.TEXT, texts = listOf("Shipping address")))
        assertEquals(FieldKind.IGNORED, kind(input = InputKind.OTHER))
    }

    @Test
    fun selectsLoginFields() {
        fun f(id: String, kind: FieldKind) = DetectedField(id, kind)

        assertEquals(
            LoginFields("user", "pass", emptyList()),
            FieldSelection.select(listOf(f("name", FieldKind.OTHER_TEXT), f("user", FieldKind.OTHER_TEXT), f("pass", FieldKind.PASSWORD))),
        )
        assertEquals(
            LoginFields("mail", "pass", emptyList()),
            FieldSelection.select(listOf(f("mail", FieldKind.USERNAME), f("x", FieldKind.OTHER_TEXT), f("pass", FieldKind.PASSWORD))),
        )
        assertEquals(
            LoginFields("mail", null, listOf("p1", "p2")),
            FieldSelection.select(
                listOf(
                    f("name", FieldKind.OTHER_TEXT),
                    f("mail", FieldKind.OTHER_TEXT),
                    f("p1", FieldKind.NEW_PASSWORD),
                    f("p2", FieldKind.NEW_PASSWORD),
                ),
            ),
        )
        assertEquals(LoginFields("mail", null, emptyList()), FieldSelection.select(listOf(f("mail", FieldKind.USERNAME))))
        assertNull(FieldSelection.select(listOf(f("name", FieldKind.OTHER_TEXT))))
        assertEquals(listOf("user", "pass"), LoginFields("user", "pass", emptyList<String>()).fillIds)
    }

    @Test
    fun normalizesHosts() {
        assertEquals("banco.es", Domains.host("https://www.Banco.es/login?x=1"))
        assertEquals("banco.es", Domains.host("banco.es"))
        assertEquals("online.banco.es", Domains.host("online.banco.es:443/"))
        assertEquals("mail.example.com", Domains.host("user@mail.example.com"))
        assertNull(Domains.host("localhost"))
        assertNull(Domains.host(""))
        assertNull(Domains.host("Mi banco"))
        assertTrue(Domains.covers("banco.es", "online.banco.es"))
        assertFalse(Domains.covers("banco.es", "otrobanco.es"))
        assertFalse(Domains.covers("banco.es", "banco.es.evil.com"))
    }

    private fun entry(title: String, url: String = "", targets: List<String> = emptyList()) =
        VaultEntry(id = title, title = title, url = url, createdAt = 0, updatedAt = 0, autofillTargets = targets)

    private val chromeCertificate = TrustedBrowsers.certificatesOf("com.android.chrome").single()
    private val chrome = AppCertificates(current = chromeCertificate, accepted = setOf(chromeCertificate))
    private val bankCertificate = "b".repeat(64)
    private val bankApp = AppCertificates(current = bankCertificate, accepted = setOf(bankCertificate))
    private val attacker = AppCertificates(current = "e".repeat(64), accepted = setOf("e".repeat(64)))

    private fun web(domain: String) = TargetResolver.resolve("com.android.chrome", chrome, domain)

    @Test
    fun trustsWebDomainsOnlyFromVerifiedBrowsers() {
        assertEquals("online.banco.es", web("online.banco.es").host)

        // Any app can claim a domain, and an app that took Chrome's package name can't have its key.
        val spoofers = listOf(
            TargetResolver.resolve("com.evil.app", attacker, "banco.es"),
            TargetResolver.resolve("com.android.chrome", attacker, "banco.es"),
            TargetResolver.resolve("com.android.chrome", null, "banco.es"),
        )
        for (target in spoofers) {
            assertNull(target.host)
            assertEquals("banco.es", target.claimedWebDomain)
            assertNull(target.key)
        }

        // A trusted browser without a domain (its own screens) is just an app.
        val chromeItself = TargetResolver.resolve("com.android.chrome", chrome, null)
        assertNull(chromeItself.host)
        assertEquals("android:com.android.chrome@$chromeCertificate", chromeItself.key)
    }

    @Test
    fun spoofedDomainsNeverMatchWebEntries() {
        val bank = entry("Banco", url = "https://www.banco.es", targets = listOf("web:banco.es"))
        assertTrue(CredentialMatcher.isExactMatch(bank, web("banco.es")))
        assertFalse(CredentialMatcher.isExactMatch(bank, TargetResolver.resolve("com.evil.app", attacker, "banco.es")))
        assertFalse(CredentialMatcher.isExactMatch(bank, TargetResolver.resolve("com.android.chrome", attacker, "banco.es")))
    }

    @Test
    fun matchesWebSitesByDomain() {
        val bank = entry("Banco", url = "https://www.banco.es")
        val remembered = entry("Tienda", targets = listOf("web:tienda.com"))

        assertTrue(CredentialMatcher.isExactMatch(bank, web("online.banco.es")))
        assertFalse(CredentialMatcher.isExactMatch(bank, web("otrobanco.es")))
        assertTrue(CredentialMatcher.isExactMatch(remembered, web("www.tienda.com")))
        assertEquals(listOf(bank), CredentialMatcher.exactMatches(listOf(remembered, bank), web("online.banco.es")))
    }

    @Test
    fun matchesAppsByPackageAndCertificate() {
        val target = TargetResolver.resolve("com.bank.app", bankApp, null)
        val linked = entry("Banco", targets = listOf("android:com.bank.app@$bankCertificate"))

        assertTrue(CredentialMatcher.isExactMatch(linked, target))
        // Same package name, different signer: a fake app installed in place of the real one.
        assertFalse(CredentialMatcher.isExactMatch(linked, TargetResolver.resolve("com.bank.app", attacker, null)))
        assertFalse(CredentialMatcher.isExactMatch(linked, TargetResolver.resolve("com.bank.app", null, null)))
        assertFalse(CredentialMatcher.isExactMatch(linked, TargetResolver.resolve("com.bank.app.fake", bankApp, null)))
        // Links without a certificate are never trusted.
        assertFalse(CredentialMatcher.isExactMatch(entry("Banco", targets = listOf("android:com.bank.app")), target))
        assertFalse(CredentialMatcher.isExactMatch(entry("Banco", url = "bank.com"), target))
    }

    @Test
    fun keepsMatchingAfterALegitimateKeyRotation() {
        val linked = entry("Banco", targets = listOf("android:com.bank.app@$bankCertificate"))
        val rotated = AppCertificates(current = "c".repeat(64), accepted = setOf(bankCertificate, "c".repeat(64)))
        assertTrue(CredentialMatcher.isExactMatch(linked, TargetResolver.resolve("com.bank.app", rotated, null)))
    }

    @Test
    fun genuineAppShowingAWebPageStillMatchesItsLink() {
        // A verified bank app whose login screen is a WebView reporting a domain.
        val linked = entry("Banco", targets = listOf("android:com.bank.app@$bankCertificate"))
        val target = TargetResolver.resolve("com.bank.app", bankApp, "login.banco.es")
        assertTrue(CredentialMatcher.isExactMatch(linked, target))
        assertNull(target.key)
    }

    @Test
    fun remembersOnlyTrustworthyTargets() {
        val instagram = entry("Instagram")
        val app = TargetResolver.resolve("com.instagram.android", bankApp, null)

        val remembered = CredentialMatcher.remember(instagram, app)
        assertEquals(listOf("android:com.instagram.android@$bankCertificate"), remembered.autofillTargets)
        assertTrue(CredentialMatcher.isExactMatch(remembered, app))
        assertEquals(remembered, CredentialMatcher.remember(remembered, app))
        assertEquals("web:banco.es", web("www.banco.es").key)

        assertEquals(instagram, CredentialMatcher.remember(instagram, TargetResolver.resolve("com.evil.app", attacker, "instagram.com")))
        assertEquals(instagram, CredentialMatcher.remember(instagram, TargetResolver.resolve("com.instagram.android", null, null)))
    }

    @Test
    fun suggestsRelatedEntries() {
        val instagram = entry("Instagram")
        val bank = entry("Banco Santander")
        assertEquals(listOf(instagram), CredentialMatcher.suggestions(listOf(bank, instagram), TargetResolver.resolve("com.instagram.android", bankApp, null)))
        assertEquals(listOf(bank), CredentialMatcher.suggestions(listOf(bank, instagram), web("www.bancosantander.es")))
    }

    @Test
    fun proposesTitlesForNewEntries() {
        assertEquals("Instagram", CredentialMatcher.suggestedTitle(TargetResolver.resolve("com.instagram.android", bankApp, null)))
        assertEquals("Bancosantander", CredentialMatcher.suggestedTitle(TargetResolver.resolve("es.bancosantander.apps", bankApp, null)))
        assertEquals("banco.es", CredentialMatcher.suggestedTitle(web("www.banco.es")))
    }

    @Test
    fun trustedBrowserTableIsWellFormed() {
        for (packageName in listOf("com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.sec.android.app.sbrowser")) {
            val certificates = TrustedBrowsers.certificatesOf(packageName)
            assertTrue(packageName, certificates.isNotEmpty())
            assertTrue(packageName, certificates.all { it.length == 64 && it.all { c -> c in "0123456789abcdef" } })
        }
        assertTrue(TrustedBrowsers.certificatesOf("com.evil.app").isEmpty())
        // A multi-signer token counts when one of its signers is the browser's key.
        val multi = AppCertificates("$chromeCertificate,${"e".repeat(64)}", setOf("$chromeCertificate,${"e".repeat(64)}"))
        assertTrue(TrustedBrowsers.isTrusted("com.android.chrome", multi))
        assertFalse(TrustedBrowsers.isTrusted("com.android.chrome", attacker))
    }
}
