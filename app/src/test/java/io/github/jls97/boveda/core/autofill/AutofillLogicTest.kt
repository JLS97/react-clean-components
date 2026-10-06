package io.github.jls97.boveda.core.autofill

import io.github.jls97.boveda.core.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.Period

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
            javaClass.getResourceAsStream("/public_suffix_list.dat")!!.use { PublicSuffixes.load(it) }
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
    fun trustedBrowserListIsRecentEnough() {
        val listDate = LocalDate.parse(TrustedBrowsers.LIST_DATE)
        val age = Period.between(listDate, LocalDate.now())
        val months = age.years * 12 + age.months
        assertTrue(
            "La lista de navegadores de confianza (TrustedBrowsers.LIST_DATE = ${TrustedBrowsers.LIST_DATE}) tiene $months meses: " +
                "regenérala desde fido2_privileged_google.json, cúrala, y actualiza LIST_DATE, EXPECTED_SIZE y el README",
            months <= 18,
        )
    }
}
