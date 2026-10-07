package io.github.jls97.boveda.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las contraseñas largas se parten en renglones iguales, no en uno lleno y una cola. */
class RenglonesTest {

    @Test
    fun loQueCabeVaEnUnaLinea() {
        assertEquals(listOf("tR8#mQ2!vL9z"), partirEnRenglones("tR8#mQ2!vL9z", 21))
        assertEquals(listOf("abc"), partirEnRenglones("abc", 3))
    }

    @Test
    fun loQueNoCabeSeReparteAPartesIguales() {
        // 24 caracteres en renglones de 21 como mucho: 12 + 12, no 21 + 3.
        assertEquals(listOf("abcdefghijkl", "mnopqrstuvwx"), partirEnRenglones("abcdefghijklmnopqrstuvwx", 21))
        // 64 en renglones de 22: 22 + 21 + 21; 10 en renglones de 4: 4 + 3 + 3.
        assertEquals(listOf(22, 21, 21), partirEnRenglones("x".repeat(64), 22).map { it.length })
        assertEquals(listOf(4, 3, 3), partirEnRenglones("x".repeat(10), 4).map { it.length })
    }

    @Test
    fun ningunRenglonPasaDelMaximoYNoSePierdeNada() {
        for (largo in 1..128) {
            for (maximo in 1..30) {
                val texto = (0 until largo).joinToString("") { (it % 10).toString() }
                val renglones = partirEnRenglones(texto, maximo)
                assertEquals(texto, renglones.joinToString(""))
                assertTrue("$largo en $maximo: $renglones", renglones.all { it.length <= maximo })
                // Los menos renglones posibles, y ninguno se lleva más de un carácter con otro.
                assertEquals((largo + maximo - 1) / maximo, renglones.size)
                assertTrue("$largo en $maximo: $renglones", renglones.maxOf { it.length } - renglones.minOf { it.length } <= 1)
            }
        }
    }
}
