package io.github.jls97.boveda.ui.vault

import io.github.jls97.boveda.core.generator.GeneratorOptions
import io.github.jls97.boveda.core.generator.PasswordGenerator
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El generador avisa de combinaciones flojas y propone una longitud digna por defecto (I-40). */
class GeneratorWarningTest {

    @Test
    fun warnsBelowSixtyBits() {
        assertNotNull(generatorWarning(0.0))
        assertNotNull(generatorWarning(26.6))
        assertNotNull(generatorWarning(59.99))
    }

    @Test
    fun staysQuietFromSixtyBits() {
        assertNull(generatorWarning(60.0))
        assertNull(generatorWarning(155.0))
    }

    @Test
    fun weakCombinationsFromTheScreenTriggerTheWarning() {
        // 8 cifras solo con números: ≈ 27 bits.
        val pinLike = GeneratorOptions(length = 8, lowercase = false, uppercase = false, symbols = false)
        assertNotNull(generatorWarning(PasswordGenerator.entropyBits(pinLike)))
        // 12 caracteres con todas las clases: ≈ 78 bits.
        assertNull(generatorWarning(PasswordGenerator.entropyBits(GeneratorOptions(length = 12))))
    }

    @Test
    fun defaultLengthIsAtLeastSixteen() {
        val defaults = GeneratorOptions()
        assertTrue(defaults.length >= MIN_DEFAULT_LENGTH)
        assertNull(generatorWarning(PasswordGenerator.entropyBits(defaults)))
    }
}
