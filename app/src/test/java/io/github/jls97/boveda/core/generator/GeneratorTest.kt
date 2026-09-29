package io.github.jls97.boveda.core.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log2

class GeneratorTest {

    @Test
    fun generatesRequestedLengthWithEveryClass() {
        val options = GeneratorOptions(length = 12)
        repeat(500) {
            val password = PasswordGenerator.generate(options)
            assertEquals(12, password.length)
            assertTrue(password.any { it in PasswordGenerator.LOWERCASE })
            assertTrue(password.any { it in PasswordGenerator.UPPERCASE })
            assertTrue(password.any { it in PasswordGenerator.DIGITS })
            assertTrue(password.any { it in PasswordGenerator.SYMBOLS })
        }
    }

    @Test
    fun respectsDisabledClassesAndAmbiguousCharacters() {
        val options = GeneratorOptions(length = 64, symbols = false, avoidAmbiguous = true)
        repeat(200) {
            val password = PasswordGenerator.generate(options)
            assertFalse(password.any { it in PasswordGenerator.SYMBOLS })
            assertFalse(password.any { it in PasswordGenerator.AMBIGUOUS })
        }
    }

    @Test
    fun eventuallyUsesTheWholePool() {
        val options = GeneratorOptions(length = 128)
        val seen = HashSet<Char>()
        repeat(200) { seen += PasswordGenerator.generate(options).toSet() }
        val pool = PasswordGenerator.characterClasses(options).joinToString("")
        assertEquals(pool.toSet(), seen)
    }

    @Test
    fun rejectsImpossibleOptions() {
        assertFalse(PasswordGenerator.canGenerate(GeneratorOptions(lowercase = false, uppercase = false, digits = false, symbols = false)))
        assertFalse(PasswordGenerator.canGenerate(GeneratorOptions(length = GeneratorOptions.MIN_LENGTH - 1)))
        assertFalse(PasswordGenerator.canGenerate(GeneratorOptions(length = GeneratorOptions.MAX_LENGTH + 1)))
        assertTrue(PasswordGenerator.canGenerate(GeneratorOptions(length = 8, lowercase = false, digits = false)))
    }

    @Test
    fun entropyMatchesPoolSize() {
        val options = GeneratorOptions(length = 20, uppercase = false, symbols = false)
        assertEquals(20 * log2(36.0), PasswordGenerator.entropyBits(options), 1e-9)
    }

    @Test
    fun strengthEstimateOrdersPasswordsSensibly() {
        assertEquals(StrengthLevel.VERY_WEAK, PasswordStrength.level(PasswordStrength.estimateBits("123456")))
        assertEquals(StrengthLevel.VERY_WEAK, PasswordStrength.level(PasswordStrength.estimateBits("aaaaaaaaaaaaaaaa")))
        assertTrue(PasswordStrength.level(PasswordStrength.estimateBits("Password123!")) <= StrengthLevel.WEAK)
        assertTrue(PasswordStrength.isAcceptableMasterPassword("tortuga-Violeta-lunes-73"))
        assertFalse(PasswordStrength.isAcceptableMasterPassword("Corta1!"))
        val generated = PasswordGenerator.generate(GeneratorOptions(length = 24))
        assertEquals(StrengthLevel.VERY_STRONG, PasswordStrength.level(PasswordStrength.estimateBits(generated)))
    }
}
