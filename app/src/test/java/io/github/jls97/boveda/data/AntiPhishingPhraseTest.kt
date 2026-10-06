package io.github.jls97.boveda.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué frases antiphishing se aceptan en la creación de la bóveda y en Ajustes (M-04). */
class AntiPhishingPhraseTest {

    @Test
    fun acceptsShortPersonalPhrases() {
        assertNull(antiPhishingPhraseProblem("gato azul"))
        assertNull(antiPhishingPhraseProblem("abc"))
        assertNull(antiPhishingPhraseProblem("a".repeat(ANTI_PHISHING_MAX_LENGTH)))
        assertNull(antiPhishingPhraseProblem("  mi frase  "))
    }

    @Test
    fun rejectsTooShortPhrases() {
        assertNotNull(antiPhishingPhraseProblem(""))
        assertNotNull(antiPhishingPhraseProblem("ab"))
        // Los espacios de los extremos no cuentan.
        assertNotNull(antiPhishingPhraseProblem("  ab  "))
        assertNotNull(antiPhishingPhraseProblem("     "))
    }

    @Test
    fun rejectsTooLongPhrases() {
        val problem = antiPhishingPhraseProblem("a".repeat(ANTI_PHISHING_MAX_LENGTH + 1))
        assertNotNull(problem)
        assertTrue(problem!!.contains("$ANTI_PHISHING_MAX_LENGTH"))
    }

    @Test
    fun rejectsSeveralLines() {
        assertNotNull(antiPhishingPhraseProblem("una\nfrase"))
    }
}
