package io.github.jls97.boveda.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Test

/** La letra con la que cada entrada se archiva en el fichero de «Tus claves». */
class FicheroTest {

    @Test
    fun letterIgnoresAccentsAndCase() {
        assertEquals("A", letraDe("amazon"))
        assertEquals("A", letraDe("Árbol de Navidad"))
        assertEquals("E", letraDe("  élite"))
        assertEquals("U", letraDe("Ü-Bahn"))
    }

    @Test
    fun enieIsItsOwnLetter() {
        assertEquals("Ñ", letraDe("ñandú"))
        assertEquals("Ñ", letraDe("Ñu"))
        assertEquals("N", letraDe("Netflix"))
    }

    @Test
    fun anythingElseGoesUnderTheHash() {
        assertEquals("#", letraDe(""))
        assertEquals("#", letraDe("   "))
        assertEquals("#", letraDe("1Password"))
        assertEquals("#", letraDe("@trabajo"))
    }
}
