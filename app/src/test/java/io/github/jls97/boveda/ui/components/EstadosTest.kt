package io.github.jls97.boveda.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class EstadosTest {

    @Test
    fun timeInSentencesRoundsUpToMinutes() {
        assertEquals("45 s", tiempoEnFrase(45))
        assertEquals("1 min", tiempoEnFrase(60))
        assertEquals("2 min", tiempoEnFrase(61))
        assertEquals("5 min", tiempoEnFrase(272))
    }

    @Test
    fun clockShowsMinutesAndSeconds() {
        assertEquals("04:32", tiempoEnReloj(272))
        assertEquals("00:05", tiempoEnReloj(5))
        assertEquals("64:00", tiempoEnReloj(3840))
    }

    @Test
    fun noticeSplitsAtTheFirstSentence() {
        assertEquals("Todavía no hay copia." to "Si pierdes el móvil, pierdes la bóveda.", partirEnAviso("Todavía no hay copia. Si pierdes el móvil, pierdes la bóveda."))
        assertEquals("Última copia hace 40 días." to null, partirEnAviso("Última copia hace 40 días."))
    }
}
