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

    @Test
    fun matchesWebSitesByDomain() {
        val bank = entry("Banco", url = "https://www.banco.es")
        val remembered = entry("Tienda", targets = listOf("web:tienda.com"))
        val web = AutofillTarget(packageName = "com.android.chrome", webDomain = "online.banco.es")

        assertTrue(CredentialMatcher.isExactMatch(bank, web))
        assertFalse(CredentialMatcher.isExactMatch(bank, AutofillTarget("com.android.chrome", "otrobanco.es")))
        assertTrue(CredentialMatcher.isExactMatch(remembered, AutofillTarget("com.android.chrome", "www.tienda.com")))
        assertEquals(listOf(bank), CredentialMatcher.exactMatches(listOf(remembered, bank), web))
    }

    @Test
    fun matchesAppsOnlyByRememberedPackage() {
        val target = AutofillTarget(packageName = "com.bank.app", webDomain = null)
        val linked = entry("Banco", targets = listOf("android:com.bank.app"))
        assertTrue(CredentialMatcher.isExactMatch(linked, target))
        assertFalse(CredentialMatcher.isExactMatch(linked, AutofillTarget("com.bank.app.fake", null)))
        assertFalse(CredentialMatcher.isExactMatch(entry("Banco", url = "bank.com"), target))
    }

    @Test
    fun suggestsRelatedEntriesAndRemembersChoices() {
        val instagram = entry("Instagram")
        val bank = entry("Banco Santander")
        val app = AutofillTarget("com.instagram.android", null)

        assertEquals(listOf(instagram), CredentialMatcher.suggestions(listOf(bank, instagram), app))
        assertEquals(listOf(bank), CredentialMatcher.suggestions(listOf(bank, instagram), AutofillTarget("org.mozilla.firefox", "www.bancosantander.es")))

        val remembered = CredentialMatcher.remember(instagram, app)
        assertEquals(listOf("android:com.instagram.android"), remembered.autofillTargets)
        assertTrue(CredentialMatcher.isExactMatch(remembered, app))
        assertEquals(remembered, CredentialMatcher.remember(remembered, app))
        assertEquals("web:banco.es", AutofillTarget("com.android.chrome", "www.banco.es").key)
    }

    @Test
    fun proposesTitlesForNewEntries() {
        assertEquals("Instagram", CredentialMatcher.suggestedTitle(AutofillTarget("com.instagram.android", null)))
        assertEquals("Bancosantander", CredentialMatcher.suggestedTitle(AutofillTarget("es.bancosantander.apps", null)))
        assertEquals("banco.es", CredentialMatcher.suggestedTitle(AutofillTarget("com.android.chrome", "www.banco.es")))
    }
}
