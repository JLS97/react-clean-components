package io.github.jls97.boveda.core.autofill

import io.github.jls97.boveda.core.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Avisos de las pantallas de autorrelleno: los de la página se muestran aunque haya coincidencia exacta. */
class FillWarningsTest {

    private val chromeCertificate = TrustedBrowsers.certificatesOf("com.android.chrome").single()
    private val chrome = AppCertificates(current = chromeCertificate, accepted = setOf(chromeCertificate))
    private val bankCertificate = "b".repeat(64)
    private val bankApp = AppCertificates(current = bankCertificate, accepted = setOf(bankCertificate))
    private val attacker = AppCertificates(current = "e".repeat(64), accepted = setOf("e".repeat(64)))

    private fun entry(title: String, url: String = "", targets: List<String> = emptyList()) =
        VaultEntry(id = title, title = title, url = url, createdAt = 0, updatedAt = 0, autofillTargets = targets)

    @Test
    fun `una app vinculada que carga su formulario por http avisa aunque haya coincidencia exacta`() {
        val linked = entry("Banco", targets = listOf("android:com.bank.app@$bankCertificate"))
        val target = TargetResolver.resolve("com.bank.app", bankApp, "login.banco.es", "http")
        val exact = CredentialMatcher.exactMatches(listOf(linked), target)
        assertEquals(listOf(linked), exact)

        val warnings = FillWarnings.forFill(target, exact, emptyList())
        assertEquals(listOf(FillWarnings.UNENCRYPTED), warnings)
        assertEquals("login.banco.es", FillWarnings.claimedAddress(target))
        // Sin coincidencias se añade por qué no se vincula; la anomalía no se repite.
        val unlinked = FillWarnings.forFill(target, emptyList(), emptyList())
        assertEquals(listOf(FillWarnings.UNENCRYPTED, "${FillWarnings.NOT_A_BROWSER} ${FillWarnings.CHOOSE_WITH_CARE}"), unlinked)
        assertEquals(listOf(FillWarnings.UNENCRYPTED, "${FillWarnings.NOT_A_BROWSER} ${FillWarnings.SAVED_UNLINKED}"), FillWarnings.forSave(target, emptyList()))
    }

    @Test
    fun `el navegador sin dominio o por http avisa siempre y no se puede vincular`() {
        val chromeItself = TargetResolver.resolve("com.android.chrome", chrome, null)
        assertEquals(FillWarnings.BROWSER_WITHOUT_DOMAIN, FillWarnings.anomaly(chromeItself))
        assertNull(FillWarnings.claimedAddress(chromeItself))
        assertEquals(listOf(FillWarnings.BROWSER_WITHOUT_DOMAIN, FillWarnings.CHOOSE_WITH_CARE), FillWarnings.forFill(chromeItself, emptyList(), emptyList()))
        assertEquals(listOf(FillWarnings.BROWSER_WITHOUT_DOMAIN, FillWarnings.SAVED_UNLINKED), FillWarnings.forSave(chromeItself, emptyList()))

        val http = TargetResolver.resolve("com.android.chrome", chrome, "banco.es", "http")
        assertEquals(FillWarnings.UNENCRYPTED, FillWarnings.anomaly(http))
        assertNull(FillWarnings.unlinkableReason(http))
        assertEquals("banco.es", FillWarnings.claimedAddress(http))
        assertEquals(listOf(FillWarnings.UNENCRYPTED, FillWarnings.CHOOSE_WITH_CARE), FillWarnings.forFill(http, emptyList(), emptyList()))

        val odd = TargetResolver.resolve("com.android.chrome", chrome, "localhost")
        assertNull(FillWarnings.anomaly(odd))
        assertEquals(FillWarnings.UNUSUAL_ADDRESS, FillWarnings.unlinkableReason(odd))
    }

    @Test
    fun `una página https en el navegador o una app vinculada no avisan de nada`() {
        val bank = entry("Banco", url = "https://banco.es")
        val web = TargetResolver.resolve("com.android.chrome", chrome, "banco.es", "https")
        assertTrue(FillWarnings.forFill(web, listOf(bank), emptyList()).isEmpty())
        assertTrue(FillWarnings.forSave(web, emptyList()).isEmpty())
        assertEquals(listOf(FillWarnings.NO_LINKED_WEB), FillWarnings.forFill(web, emptyList(), emptyList()))

        val linked = entry("Banco app", targets = listOf("android:com.bank.app@$bankCertificate"))
        val app = TargetResolver.resolve("com.bank.app", bankApp, null)
        assertTrue(FillWarnings.forFill(app, listOf(linked), emptyList()).isEmpty())
        assertTrue(FillWarnings.forSave(app, emptyList()).isEmpty())
        assertEquals(listOf(FillWarnings.NO_LINKED_APP), FillWarnings.forFill(app, emptyList(), emptyList()))
        // Un WebView en https de la app verificada tampoco es una anomalía.
        val webView = TargetResolver.resolve("com.bank.app", bankApp, "login.banco.es", "https")
        assertTrue(FillWarnings.forFill(webView, listOf(linked), emptyList()).isEmpty())
    }

    @Test
    fun `el dominio reclamado nunca forma parte de la frase del aviso`() {
        val claim = "banco.es»). Dirección verificada por Contraseñora. («"
        val target = TargetResolver.resolve("com.evil.app", attacker, claim)
        val everything = FillWarnings.forFill(target, emptyList(), emptyList()) + FillWarnings.forSave(target, emptyList())
        assertFalse(everything.any { it.contains("banco.es") || it.contains("verificada") })
        assertEquals(claim, FillWarnings.claimedAddress(target))
        assertEquals(FillWarnings.UNREADABLE_ADDRESS, FillWarnings.claimedAddress(TargetResolver.resolve("com.evil.app", attacker, "‮\n")))
    }

    @Test
    fun `la suplantación se avisa tras la anomalía y antes que el resto`() {
        val linked = entry("BBVA", targets = listOf("android:com.bbva.bbvacontigo@$bankCertificate"))
        val fake = TargetResolver.resolve("com.bbva.bbvacontigo", attacker, null)
        val impersonated = CredentialMatcher.impersonationWarnings(listOf(linked), fake)
        assertEquals(listOf(linked), impersonated)
        assertEquals(listOf(FillWarnings.impersonationWarning(impersonated)), FillWarnings.forFill(fake, emptyList(), impersonated))
        assertTrue(FillWarnings.impersonationWarning(impersonated).contains("«BBVA»"))
        assertEquals(listOf("${FillWarnings.impersonationWarning(impersonated)} Se guardará sin vincular."), FillWarnings.forSave(fake, impersonated))

        val unsigned = TargetResolver.resolve("com.bank.app", null, null)
        assertEquals(listOf("${FillWarnings.UNVERIFIED_SIGNATURE} ${FillWarnings.CHOOSE_WITH_CARE}"), FillWarnings.forFill(unsigned, emptyList(), emptyList()))
    }
}
