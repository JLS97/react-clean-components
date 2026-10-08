package io.github.jls97.boveda.ui.vault

import io.github.jls97.boveda.core.vault.SealedOtp
import io.github.jls97.boveda.core.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los textos que la ficha compone: el resumen bajo el título y los cortes de las direcciones. */
class FichaTextosTest {

    private fun entrada(destinos: List<String> = emptyList(), conCodigo: Boolean = false) = VaultEntry(
        id = "x",
        title = "Banco",
        createdAt = 0,
        updatedAt = 0,
        autofillTargets = destinos,
        otp = if (conCodigo) SealedOtp(byteArrayOf(1)) else null,
    )

    @Test
    fun elResumenDiceSiHay2FAYDondeRellena() {
        assertNull(resumenDeFicha(entrada()))
        assertEquals("Con 2FA", resumenDeFicha(entrada(conCodigo = true)))
        assertEquals("Autorrelleno en 1 web", resumenDeFicha(entrada(listOf("web:banco.es"))))
        assertEquals(
            "Con 2FA · autorrelleno en 2 webs y 1 app",
            resumenDeFicha(entrada(listOf("web:banco.es", "web:m.banco.es", "android:es.banco.app@ab12"), conCodigo = true)),
        )
        assertEquals("Autorrelleno en 3 apps", resumenDeFicha(entrada(List(3) { "android:app$it@cd34" })))
    }

    @Test
    fun lasDireccionesSoloSePartenTrasPuntoArrobaOBarra() {
        assertEquals("ana.​vega@​correo.​es", conCortes("ana.vega@correo.es"))
        assertEquals("banco.​es/​acceso", conCortes("banco.es/acceso"))
        assertEquals("12345678Z", conCortes("12345678Z"))
        // Quitando los espacios de ancho cero queda lo mismo que se copia.
        val texto = "com.banco.app (firma 3a5f9c01…)"
        assertEquals(texto, conCortes(texto).replace("​", ""))
    }
}
