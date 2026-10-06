package io.github.jls97.boveda.core.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El algoritmo de la Public Suffix List con una lista mínima: reglas exactas, comodines y excepciones. */
class PublicSuffixesTest {

    private fun load(vararg rules: String) = PublicSuffixes.load(rules.asSequence())

    private fun withRules(vararg rules: String, block: () -> Unit) {
        load(*rules)
        try {
            block()
        } finally {
            // Deja la lista real para el resto de tests del proceso.
            javaClass.getResourceAsStream("/public_suffix_list.dat")!!.use { PublicSuffixes.load(it) }
        }
    }

    @Test
    fun appliesExactWildcardAndExceptionRules() = withRules(
        "// comentario",
        "",
        "com",
        "es",
        "co.uk",
        "uk",
        "*.ck",
        "!www.ck",
        "github.io  // texto tras la regla se ignora",
        "ευ",
    ) {
        assertEquals("es", PublicSuffixes.publicSuffix("banco.es"))
        assertEquals("banco.es", PublicSuffixes.registrableDomain("online.banco.es"))
        assertEquals("banco.co.uk", PublicSuffixes.registrableDomain("www.banco.co.uk"))
        assertTrue(PublicSuffixes.isPublicSuffix("co.uk"))
        assertNull(PublicSuffixes.registrableDomain("co.uk"))
        // Comodín: cualquier etiqueta bajo ck es sufijo público, salvo la excepción www.ck.
        assertEquals("foo.ck", PublicSuffixes.publicSuffix("bar.foo.ck"))
        assertEquals("bar.foo.ck", PublicSuffixes.registrableDomain("bar.foo.ck"))
        assertEquals("ck", PublicSuffixes.publicSuffix("www.ck"))
        assertEquals("www.ck", PublicSuffixes.registrableDomain("www.ck"))
        assertEquals("www.ck", PublicSuffixes.registrableDomain("mail.www.ck"))
        // Sección privada: el host compartido es sufijo público.
        assertTrue(PublicSuffixes.isPublicSuffix("github.io"))
        assertEquals("usuario.github.io", PublicSuffixes.registrableDomain("usuario.github.io"))
        // Un TLD desconocido cuenta como sufijo público por sí mismo (regla implícita «*»).
        assertEquals("internal", PublicSuffixes.publicSuffix("banco.internal"))
        assertEquals("banco.internal", PublicSuffixes.registrableDomain("banco.internal"))
        assertNull(PublicSuffixes.registrableDomain("es"))
        // Las reglas Unicode se comparan en punycode.
        assertTrue(PublicSuffixes.isPublicSuffix("xn--qxa6a"))
        assertEquals("banco.xn--qxa6a", PublicSuffixes.registrableDomain("www.banco.xn--qxa6a"))
        // Mayúsculas y punto final se toleran.
        assertEquals("banco.es", PublicSuffixes.registrableDomain("Online.Banco.ES."))
        assertNull(PublicSuffixes.publicSuffix("banco..es"))
    }

    @Test
    fun realListKnowsSharedHostsAndCountryCodes() {
        javaClass.getResourceAsStream("/public_suffix_list.dat")!!.use { PublicSuffixes.load(it) }
        assertTrue(PublicSuffixes.isLoaded)
        for (suffix in listOf("es", "com", "co.uk", "github.io", "blogspot.com", "netlify.app", "pages.dev", "web.app")) {
            assertTrue(suffix, PublicSuffixes.isPublicSuffix(suffix))
        }
        assertFalse(PublicSuffixes.isPublicSuffix("google.com"))
        assertEquals("google.com", PublicSuffixes.registrableDomain("sites.google.com"))
        assertEquals("banco.es", PublicSuffixes.registrableDomain("online.banco.es"))
        assertEquals("www.ck", PublicSuffixes.registrableDomain("www.ck"))
    }
}
