package io.github.jls97.boveda.core.autofill

import io.github.jls97.boveda.core.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class AutofillLogicTest {

    private companion object {
        /** Entradas de TrustedBrowsers; se actualiza a la vez que la lista. */
        const val EXPECTED_SIZE = 57

        /** Claves públicas de prueba de AOSP (su clave privada está publicada): nunca pueden ser de confianza. */
        val AOSP_TEST_KEYS = mapOf(
            "testkey" to "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc",
            "platform" to "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8",
            "shared" to "28bbfe4a7b97e74681dc55c2fbb6ccb8d6c74963733f6af6ae74d8c3a6e879fd",
            "media" to "465983f7791f2abeb43ea2cbdc7f21a8260b72bc08a55c839fc1a43bc741a81e",
            "networkstack" to "e1dbadce60dc080d15b58a014b0dcf9400e24de23fa00b287a5a982bfebda2ee",
        )
    }

    @Before
    fun loadPublicSuffixList() {
        if (!PublicSuffixes.isLoaded) {
            loadRealPublicSuffixList()
        }
    }

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
        assertEquals(FieldKind.OTP, kind(hints = listOf("smsOTPCode"), input = InputKind.TEXT))
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
    fun recognizes2faCodeFields() {
        assertEquals(FieldKind.OTP, kind(hints = listOf("2faAppOTPCode")))
        assertEquals(FieldKind.OTP, kind(hints = listOf("one-time-code"), input = InputKind.TEXT))
        assertEquals(FieldKind.OTP, kind(tag = "input", attributes = mapOf("type" to "text", "autocomplete" to "one-time-code")))
        assertEquals(FieldKind.OTP, kind(tag = "input", attributes = mapOf("type" to "tel", "name" to "totpPin")))
        assertEquals(FieldKind.OTP, kind(input = InputKind.OTHER, texts = listOf("Código de verificación")))
        assertEquals(FieldKind.OTP, kind(input = InputKind.TEXT, texts = listOf("otp_input")))
        assertEquals(FieldKind.OTP, kind(input = InputKind.TEXT, texts = listOf("Introduce el código de tu app de autenticador")))
        assertEquals(FieldKind.OTP, kind(input = InputKind.PASSWORD, texts = listOf("One-time password")))
        assertEquals(FieldKind.OTP, kind(input = InputKind.OTHER, texts = listOf("mfaCode")))
        // Look-alikes that are not 2FA codes.
        assertEquals(FieldKind.OTHER_TEXT, kind(input = InputKind.TEXT, texts = listOf("Código postal")))
        assertEquals(FieldKind.PASSWORD, kind(input = InputKind.TEXT, texts = listOf("PIN")))
        assertEquals(FieldKind.IGNORED, kind(input = InputKind.OTHER, texts = listOf("One-time donation")))
    }

    @Test
    fun selects2faCodeFields() {
        fun f(id: String, kind: FieldKind) = DetectedField(id, kind)

        // A second-step screen with only the code.
        assertEquals(LoginFields(null, null, emptyList(), otp = "code"), FieldSelection.select(listOf(f("code", FieldKind.OTP))))
        // The code field is never mistaken for the username.
        assertEquals(
            LoginFields("user", "pass", emptyList(), otp = "code"),
            FieldSelection.select(listOf(f("user", FieldKind.OTHER_TEXT), f("pass", FieldKind.PASSWORD), f("code", FieldKind.OTP))),
        )
        assertEquals(listOf("user", "pass"), LoginFields("user", "pass", emptyList(), otp = "code").fillIds)
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
        assertEquals("banco.es", Domains.host("BANCO.ES."))
        // Userinfo never passes for the host, and IPv6 literals aren't domains.
        assertEquals("evil.com", Domains.host("https://user:pw@banco.es@evil.com/"))
        assertNull(Domains.host("[::1]"))
        assertNull(Domains.host("https://[::1]:8443/"))
        assertNull(Domains.host("banco..es"))
        assertNull(Domains.host("-banco.es"))
        assertNull(Domains.host("banco-.es"))
        assertNull(Domains.host("banco_es.com"))
        assertTrue(Domains.covers("banco.es", "online.banco.es"))
        assertFalse(Domains.covers("banco.es", "otrobanco.es"))
        assertFalse(Domains.covers("banco.es", "banco.es.evil.com"))
    }

    @Test
    fun publicSuffixListIsLoadedFromTestResources() {
        assertTrue(PublicSuffixes.isLoaded)
        assertEquals("es", PublicSuffixes.publicSuffix("online.banco.es"))
        assertEquals("banco.es", PublicSuffixes.registrableDomain("online.banco.es"))
        assertTrue(PublicSuffixes.isPublicSuffix("co.uk"))
        // Sección privada: hosts de contenido de usuario.
        assertTrue(PublicSuffixes.isPublicSuffix("github.io"))
        assertTrue(PublicSuffixes.isPublicSuffix("blogspot.com"))
        assertEquals("usuario.github.io", PublicSuffixes.registrableDomain("www.usuario.github.io"))
    }

    @Test
    fun sharedSuffixesCoverOnlyThemselves() {
        // Un subdominio del sitio guardado se rellena: es la política documentada.
        assertTrue(Domains.covers("banco.es", "online.banco.es"))
        assertTrue(Domains.covers("banco.es", "abandonado.banco.es"))
        assertTrue(Domains.covers("usuario.github.io", "app.usuario.github.io"))
        // Un sufijo público guardado como url solo coincide con el host exacto.
        assertTrue(Domains.covers("github.io", "github.io"))
        assertFalse(Domains.covers("github.io", "atacante.github.io"))
        assertFalse(Domains.covers("blogspot.com", "aviso-seguridad.blogspot.com"))
        assertFalse(Domains.covers("co.uk", "banco.co.uk"))
        // Direcciones IP: nunca por sufijo.
        assertFalse(Domains.covers("1.1", "192.168.1.1"))
        assertTrue(Domains.covers("192.168.1.1", "192.168.1.1"))

        val github = entry("GitHub Pages", url = "https://github.io")
        assertTrue(CredentialMatcher.isExactMatch(github, web("github.io")))
        assertFalse(CredentialMatcher.isExactMatch(github, web("atacante.github.io")))
        assertTrue(CredentialMatcher.isExactMatch(entry("Blog", url = "miblog.blogspot.com"), web("miblog.blogspot.com")))
        assertFalse(CredentialMatcher.isExactMatch(entry("Blog", url = "blogspot.com"), web("miblog.blogspot.com")))
    }

    @Test
    fun normalizesInternationalizedDomainsToPunycode() {
        val homograph = "b\u0430nco.es" // «а» cirílica
        assertEquals("xn--bnco-53d.es", Domains.host("https://www.$homograph/login"))
        assertEquals("xn--bnco-53d.es", Domains.host("xn--bnco-53d.es"))
        assertTrue(Domains.isIdn("xn--bnco-53d.es"))
        assertFalse(Domains.isIdn("banco.es"))
        assertFalse(Domains.covers("banco.es", "xn--bnco-53d.es"))
        assertFalse(Domains.covers("xn--bnco-53d.es", "banco.es"))

        val bank = entry("Banco", url = "https://www.banco.es", targets = listOf("web:banco.es"))
        val lookAlike = web(homograph)
        assertEquals("xn--bnco-53d.es", lookAlike.host)
        assertTrue(lookAlike.isIdn)
        assertFalse(web("banco.es").isIdn)
        assertFalse(CredentialMatcher.isExactMatch(bank, lookAlike))
        assertTrue(CredentialMatcher.suggestions(listOf(bank), lookAlike).isEmpty())
        // Ambas grafías de una misma entrada IDN coinciden entre sí.
        val idnEntry = entry("Banco IDN", url = "https://$homograph")
        assertTrue(CredentialMatcher.isExactMatch(idnEntry, web("xn--bnco-53d.es")))
        assertTrue(CredentialMatcher.isExactMatch(idnEntry, lookAlike))
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

        // A trusted browser without a domain (its own screens, about:blank, a data: page) is shown
        // as an app but can't be linked: the link would reach every page without a domain.
        val chromeItself = TargetResolver.resolve("com.android.chrome", chrome, null)
        assertNull(chromeItself.host)
        assertNull(chromeItself.claimedWebDomain)
        assertTrue(chromeItself.trustedBrowser)
        assertEquals("com.android.chrome", chromeItself.label)
        assertNull(chromeItself.key)
        assertEquals(entry("Chrome"), CredentialMatcher.remember(entry("Chrome"), chromeItself))
        // An app that isn't a browser is still linkable without a domain.
        assertFalse(TargetResolver.resolve("com.bank.app", bankApp, null).trustedBrowser)
        assertEquals("android:com.bank.app@$bankCertificate", TargetResolver.resolve("com.bank.app", bankApp, null).key)
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
    fun movesAppLinksToTheCurrentCertificateAfterAKeyRotation() {
        val newCertificate = "c".repeat(64)
        val rotated = TargetResolver.resolve("com.bank.app", AppCertificates(current = newCertificate, accepted = setOf(bankCertificate, newCertificate)), null)
        val linked = entry("Banco", targets = listOf("web:banco.es", "android:com.bank.app@$bankCertificate"))

        // The first match through the old certificate rewrites the link; other links are untouched.
        val migrated = CredentialMatcher.remember(linked, rotated)
        assertEquals(listOf("web:banco.es", "android:com.bank.app@$newCertificate"), migrated.autofillTargets)
        assertTrue(CredentialMatcher.isExactMatch(migrated, rotated))
        assertEquals(migrated, CredentialMatcher.remember(migrated, rotated))
        // Two links to the same app (one old, one current) collapse into one.
        val twice = entry("Banco", targets = listOf("android:com.bank.app@$bankCertificate", "android:com.bank.app@$newCertificate"))
        assertEquals(listOf("android:com.bank.app@$newCertificate"), CredentialMatcher.remember(twice, rotated).autofillTargets)

        // The link also moves when the target itself can't be linked (a WebView reporting a domain).
        val webView = TargetResolver.resolve("com.bank.app", AppCertificates(current = newCertificate, accepted = setOf(bankCertificate, newCertificate)), "login.banco.es")
        assertNull(webView.key)
        assertEquals(migrated.autofillTargets, CredentialMatcher.remember(linked, webView).autofillTargets)

        // Afterwards an app signed only with the old (possibly leaked) key no longer matches: it is flagged.
        val oldKeyOnly = TargetResolver.resolve("com.bank.app", bankApp, null)
        assertFalse(CredentialMatcher.isExactMatch(migrated, oldKeyOnly))
        assertEquals(listOf(migrated), CredentialMatcher.impersonationWarnings(listOf(migrated), oldKeyOnly))
        // Links to other apps or under unknown certificates are left as they are (a new link is added as usual).
        val other = entry("Otra", targets = listOf("android:com.other.app@$bankCertificate", "android:com.bank.app@${"d".repeat(64)}"))
        assertEquals(other.autofillTargets + "android:com.bank.app@$newCertificate", CredentialMatcher.remember(other, rotated).autofillTargets)
    }

    @Test
    fun legacyLinksToATrustedBrowserAreInert() {
        // Older versions could link an entry to Chrome itself; such a link must reach no page.
        val legacy = entry("Chrome", targets = listOf("android:com.android.chrome@$chromeCertificate"))
        val chromeItself = TargetResolver.resolve("com.android.chrome", chrome, null)
        val unencrypted = TargetResolver.resolve("com.android.chrome", chrome, "banco.es", "http")
        assertTrue(unencrypted.unencrypted)
        assertNull(unencrypted.host)
        for (target in listOf(chromeItself, unencrypted)) {
            assertFalse(CredentialMatcher.isExactMatch(legacy, target))
            assertTrue(CredentialMatcher.exactMatches(listOf(legacy), target).isEmpty())
            // The certificate is Chrome's own: not an impersonation either.
            assertTrue(CredentialMatcher.impersonationWarnings(listOf(legacy), target).isEmpty())
            assertEquals(legacy, CredentialMatcher.remember(legacy, target))
        }
        assertFalse(CredentialMatcher.isExactMatch(legacy, web("banco.es")))
        // An app that isn't a browser keeps matching through its link without a domain.
        val bank = entry("Banco", targets = listOf("android:com.bank.app@$bankCertificate"))
        assertTrue(CredentialMatcher.isExactMatch(bank, TargetResolver.resolve("com.bank.app", bankApp, null)))
    }

    @Test
    fun coversOnlyTheSameHostWithoutThePublicSuffixList() {
        PublicSuffixes.unload()
        try {
            assertFalse(PublicSuffixes.isLoaded)
            assertTrue(Domains.covers("banco.es", "banco.es"))
            assertFalse(Domains.covers("banco.es", "online.banco.es"))
            assertFalse(Domains.covers("github.io", "atacante.github.io"))
            val bank = entry("Banco", url = "https://banco.es")
            assertTrue(CredentialMatcher.isExactMatch(bank, web("banco.es")))
            assertFalse(CredentialMatcher.isExactMatch(bank, web("online.banco.es")))
        } finally {
            loadRealPublicSuffixList()
        }
        assertTrue(Domains.covers("banco.es", "online.banco.es"))
    }

    @Test
    fun matchesWebLinksSavedWithUnicodeLettersByOlderVersions() {
        val legacy = entry("Ayuntamiento", targets = listOf("web:España.es", "web:münchen.de"))
        val request = web("xn--espaa-rta.es")
        assertEquals("xn--espaa-rta.es", request.host)
        assertTrue(CredentialMatcher.isExactMatch(legacy, request))
        assertTrue(CredentialMatcher.isExactMatch(legacy, web("sede.xn--espaa-rta.es")))
        assertTrue(CredentialMatcher.isExactMatch(legacy, web("xn--mnchen-3ya.de")))
        assertFalse(CredentialMatcher.isExactMatch(legacy, web("espana.es")))
        // Remembering rewrites the links in punycode, without adding a duplicate.
        val remembered = CredentialMatcher.remember(legacy, request)
        assertEquals(listOf("web:xn--espaa-rta.es", "web:xn--mnchen-3ya.de"), remembered.autofillTargets)
        assertEquals(remembered, CredentialMatcher.remember(remembered, request))
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
        // Only entries without any anchor (first use) are suggested by name.
        val instagram = entry("Instagram")
        val bank = entry("Banco Santander")
        assertEquals(listOf(instagram), CredentialMatcher.suggestions(listOf(bank, instagram), TargetResolver.resolve("com.instagram.android", bankApp, null)))
        assertEquals(listOf(bank), CredentialMatcher.suggestions(listOf(bank, instagram), web("www.bancosantander.es")))
        assertTrue(CredentialMatcher.suggestions(listOf(bank, instagram), web("otracosa.com")).isEmpty())
        assertTrue(CredentialMatcher.suggestions(listOf(bank, instagram), TargetResolver.resolve("com.otracosa.app", bankApp, null)).isEmpty())
    }

    @Test
    fun neverSuggestsEntriesAnchoredToAnotherDomainOrApp() {
        val instagramWeb = entry("Instagram", url = "https://www.instagram.com")
        val instagramApp = entry("Instagram", targets = listOf("android:com.instagram.android@$bankCertificate"))
        val paypal = entry("PayPal", targets = listOf("web:paypal.com"))
        val entries = listOf(instagramWeb, instagramApp, paypal)

        // Phishing domains and look-alike packages share words with the real entries: no hint.
        for (phishing in listOf("instagram-login.com", "secure-paypal.net", "instagram.com.evil.net", "paypal.com-verify.es")) {
            assertTrue(phishing, CredentialMatcher.suggestions(entries, web(phishing)).isEmpty())
        }
        for (fakePackage in listOf("com.instagram.fake", "com.instagram.lite.free", "com.evil.paypal", "www.instagram.com")) {
            assertTrue(fakePackage, CredentialMatcher.suggestions(entries, TargetResolver.resolve(fakePackage, attacker, null)).isEmpty())
        }
        // A web entry is anchored to its domain: a related app isn't a suggestion either.
        assertTrue(CredentialMatcher.suggestions(entries, TargetResolver.resolve("com.instagram.android", bankApp, null)).isEmpty())
        // Same package name under another signature: handled by impersonationWarnings, never a hint.
        assertTrue(CredentialMatcher.suggestions(entries, TargetResolver.resolve("com.instagram.android", attacker, null)).isEmpty())
        // The real app and site still match exactly, which is a different list.
        assertEquals(listOf(instagramApp), CredentialMatcher.exactMatches(entries, TargetResolver.resolve("com.instagram.android", bankApp, null)))
        assertEquals(listOf(instagramWeb), CredentialMatcher.exactMatches(entries, web("instagram.com")))
    }

    @Test
    fun suggestsEntriesOfTheSameRegistrableDomain() {
        val online = entry("Mi banco", url = "https://online.banco.es")
        val linked = entry("Tarjetas", targets = listOf("web:tarjetas.banco.es"))
        val other = entry("Otro banco", url = "https://online.otrobanco.es")
        val entries = listOf(other, online, linked)

        // Another host of banco.es isn't covered by `online.banco.es`, but belongs to the same owner.
        assertTrue(CredentialMatcher.exactMatches(entries, web("app.banco.es")).isEmpty())
        assertEquals(listOf(online, linked), CredentialMatcher.suggestions(entries, web("app.banco.es")))
        assertEquals(listOf(online, linked), CredentialMatcher.suggestions(entries, web("banco.es")))
        // A different registrable domain never is, even when it ends the same way.
        assertTrue(CredentialMatcher.suggestions(entries, web("banco.es.evil.com")).isEmpty())
        assertTrue(CredentialMatcher.suggestions(entries, web("nobanco.es")).isEmpty())
        // Under a public suffix every name has a different owner.
        val pages = entry("Mi web", url = "https://blog.usuario.github.io")
        assertTrue(CredentialMatcher.suggestions(listOf(pages), web("atacante.github.io")).isEmpty())
        assertTrue(CredentialMatcher.suggestions(listOf(pages), web("github.io")).isEmpty())
        assertEquals(listOf(pages), CredentialMatcher.suggestions(listOf(pages), web("fotos.usuario.github.io")))
    }

    @Test
    fun warnsAboutSamePackageUnderAnotherSignature() {
        val linked = entry("BBVA", targets = listOf("android:com.bbva.bbvacontigo@$bankCertificate"))
        val unrelated = entry("Instagram", targets = listOf("android:com.instagram.android@$bankCertificate"))
        val legacy = entry("Viejo", targets = listOf("android:com.bbva.bbvacontigo"))
        val entries = listOf(unrelated, linked, legacy)

        // Fake app with the bank's package name and its own key: flagged, and never a suggestion.
        val fake = TargetResolver.resolve("com.bbva.bbvacontigo", attacker, null)
        assertEquals(listOf(linked), CredentialMatcher.impersonationWarnings(entries, fake))
        assertTrue(CredentialMatcher.suggestions(entries, fake).isEmpty())
        // Unverifiable signature counts as another signature too.
        assertEquals(listOf(linked), CredentialMatcher.impersonationWarnings(entries, TargetResolver.resolve("com.bbva.bbvacontigo", null, null)))

        // The real app, also after a key rotation, is an exact match and nothing to warn about.
        assertTrue(CredentialMatcher.impersonationWarnings(entries, TargetResolver.resolve("com.bbva.bbvacontigo", bankApp, null)).isEmpty())
        val rotated = AppCertificates(current = "c".repeat(64), accepted = setOf(bankCertificate, "c".repeat(64)))
        assertTrue(CredentialMatcher.impersonationWarnings(entries, TargetResolver.resolve("com.bbva.bbvacontigo", rotated, null)).isEmpty())
        // Other packages and web sites don't trigger it.
        assertTrue(CredentialMatcher.impersonationWarnings(entries, TargetResolver.resolve("com.bbva.bbvacontigo.fake", attacker, null)).isEmpty())
        assertTrue(CredentialMatcher.impersonationWarnings(entries, web("bbva.es")).isEmpty())
    }

    @Test
    fun proposesTitlesForNewEntries() {
        assertEquals("Instagram", CredentialMatcher.suggestedTitle(TargetResolver.resolve("com.instagram.android", bankApp, null)))
        assertEquals("Bancosantander", CredentialMatcher.suggestedTitle(TargetResolver.resolve("es.bancosantander.apps", bankApp, null)))
        assertEquals("banco.es", CredentialMatcher.suggestedTitle(web("www.banco.es")))
    }

    @Test
    fun neverProposesTheTitleOfAnEntryAnchoredElsewhere() {
        val real = entry("Instagram", targets = listOf("android:com.instagram.android@$bankCertificate"))
        val site = entry("PayPal", url = "https://www.paypal.com")
        val loose = entry("Twitter")
        val entries = listOf(real, site, loose)

        // A fake package whose last word is the brand gets its full name, never the brand.
        assertEquals("com.evil.instagram", CredentialMatcher.suggestedTitle(TargetResolver.resolve("com.evil.instagram", attacker, null), entries))
        assertEquals("app.login.paypal", CredentialMatcher.suggestedTitle(TargetResolver.resolve("app.login.paypal", attacker, null), entries))
        // The real app (exact match) and entries without anchor keep the short name.
        assertEquals("Instagram", CredentialMatcher.suggestedTitle(TargetResolver.resolve("com.instagram.android", bankApp, null), entries))
        assertEquals("Twitter", CredentialMatcher.suggestedTitle(TargetResolver.resolve("com.twitter.android", attacker, null), entries))
        assertEquals("Spotify", CredentialMatcher.suggestedTitle(TargetResolver.resolve("com.spotify.app", attacker, null), entries))
    }

    @Test
    fun sanitizesTextComingFromOtherApps() {
        // Bidirectional overrides, isolates, marks, zero-width and control characters, line breaks.
        val hostile = "banco.es‮⁦⁧⁨⁩‎‏​‍﻿\u0000\u001B\n\r  ») verificada"
        assertEquals("banco.es») verificada", ExternalText.sanitize(hostile))
        assertEquals("a".repeat(100), ExternalText.sanitize("a".repeat(500)))
        assertEquals("", ExternalText.sanitize("‮‮\n"))
        // Ordinary text, accents and emoji survive; a surrogate pair is never cut in half.
        assertEquals("María López 😀", ExternalText.sanitize("María López 😀"))
        assertEquals("a".repeat(99), ExternalText.sanitize("a".repeat(99) + "😀"))

        // The claimed domain reaches the screen already clean and bounded.
        val claimed = TargetResolver.resolve("com.evil.app", attacker, "‮banco.es\n" + "x".repeat(200)).claimedWebDomain
        assertEquals("banco.es" + "x".repeat(92), claimed)
        assertEquals(100, claimed!!.length)
        // A trusted browser's domain is validated, not trimmed: a long host can't be cut into another one.
        val long = "a".repeat(45) + "." + "a".repeat(45) + ".banco.es.evil.com"
        assertEquals(".banco.es", long.take(100).takeLast(9))
        assertEquals(long, web(long).host)
        assertNull(web("banco.es‮").host)
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

    @Test
    fun trustsBrowsersOnlyByTheirCurrentCertificate() {
        // The browser rotated its key and the table wasn't updated yet: the old key may have leaked,
        // and an app signed only with it would carry the same history, so it is no longer trusted.
        val rotatedAway = AppCertificates(current = "e".repeat(64), accepted = setOf(chromeCertificate, "e".repeat(64)))
        assertFalse(TrustedBrowsers.isTrusted("com.android.chrome", rotatedAway))
        assertNull(TargetResolver.resolve("com.android.chrome", rotatedAway, "banco.es").host)
        // The current key being known is enough, whatever the history holds.
        val rotatedInto = AppCertificates(current = chromeCertificate, accepted = setOf("e".repeat(64), chromeCertificate))
        assertTrue(TrustedBrowsers.isTrusted("com.android.chrome", rotatedInto))
        // A multi-signer history doesn't count either unless a current signer is known.
        val oldMulti = AppCertificates("${"e".repeat(64)},${"f".repeat(64)}", setOf("$chromeCertificate,${"e".repeat(64)}", "${"e".repeat(64)},${"f".repeat(64)}"))
        assertFalse(TrustedBrowsers.isTrusted("com.android.chrome", oldMulti))
    }

    @Test
    fun recognisesPackageNames() {
        for (valid in listOf("com.android.chrome", "a.b", "io.github.jls97.boveda", "com.Bank_1.app2")) {
            assertTrue(valid, TargetResolver.isPackageName(valid))
        }
        for (invalid in listOf("", " ", "chrome", "com..chrome", ".com.chrome", "com.chrome.", "com.1bank.app", "com.bank app", "com/bank", "banco.es/login", "a.".repeat(130) + "b")) {
            assertFalse(invalid, TargetResolver.isPackageName(invalid))
        }
    }

    @Test
    fun everyTrustedBrowserEntryIsSoundAndNotAnAospTestKey() {
        val table = TrustedBrowsers.all()
        assertEquals("Actualiza EXPECTED_SIZE al cambiar la lista de navegadores", EXPECTED_SIZE, table.size)
        for ((packageName, certificates) in table) {
            assertTrue("$packageName sin huellas", certificates.isNotEmpty())
            assertFalse("$packageName es un paquete de depuración", packageName.endsWith(".debug"))
            for (fingerprint in certificates) {
                assertTrue(
                    "$packageName: huella mal formada «$fingerprint» (deben ser 64 hex minúsculas)",
                    fingerprint.length == 64 && fingerprint.all { c -> c in "0123456789abcdef" },
                )
                for ((keyName, testKey) in AOSP_TEST_KEYS) {
                    assertFalse("$packageName acepta la clave pública de prueba «$keyName» de AOSP", fingerprint == testKey)
                }
            }
        }
        // Las apps que no son navegadores de consumo quedaron fuera al curar la lista de Google.
        for (packageName in listOf("com.google.android.gms", "com.fido.fido2client", "com.oplus.credential", "com.citrix.Receiver", "com.zoho.primeum.stable")) {
            assertTrue("$packageName no es un navegador", TrustedBrowsers.certificatesOf(packageName).isEmpty())
        }
    }

    @Test
    fun trustedBrowserListDateIsIsoAndItsAgeIsJudgedOnAnInjectedDate() {
        // El formato es lo único que se comprueba contra el calendario real: la caducidad de la
        // lista (18 meses) la vigila un paso de la CI que solo avisa, para que la suite no se ponga
        // en rojo un día fijo sin que ningún commit lo provoque (R07-6, R07-7).
        val listDate = LocalDate.parse(TrustedBrowsers.LIST_DATE)
        assertEquals(TrustedBrowsers.LIST_DATE, listDate.toString())
        assertEquals(18, TrustedBrowsers.MAX_LIST_AGE_MONTHS)

        assertEquals(0, TrustedBrowsers.listAgeInMonths(listDate))
        assertEquals(0, TrustedBrowsers.listAgeInMonths(listDate.plusDays(27)))
        assertEquals(1, TrustedBrowsers.listAgeInMonths(listDate.plusMonths(1)))
        assertEquals(18, TrustedBrowsers.listAgeInMonths(listDate.plusMonths(18)))
        assertEquals(18, TrustedBrowsers.listAgeInMonths(listDate.plusMonths(19).minusDays(1)))
        assertEquals(19, TrustedBrowsers.listAgeInMonths(listDate.plusMonths(19)))
        assertEquals(-1, TrustedBrowsers.listAgeInMonths(listDate.minusMonths(1)))

        assertTrue(TrustedBrowsers.listIsRecent(listDate))
        assertTrue(TrustedBrowsers.listIsRecent(listDate.plusMonths(18)))
        assertTrue(TrustedBrowsers.listIsRecent(listDate.plusMonths(19).minusDays(1)))
        assertFalse(TrustedBrowsers.listIsRecent(listDate.plusMonths(19)))
        assertFalse(TrustedBrowsers.listIsRecent(listDate.plusYears(5)))
    }
}
