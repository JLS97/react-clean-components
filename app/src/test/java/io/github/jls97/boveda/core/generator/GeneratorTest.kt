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

    @Test
    fun strengthRejectsLongSequencesAndRepeatedPatternsAsMasterPassword() {
        // Long enough for the length rule, and among the first guesses of any pattern dictionary.
        val patterns = listOf(
            "aaaaaaaaaaaaaaaa",
            "a".repeat(49),
            "abcdefghijklmnop",
            "abcdefghijklmnopqrstuvwxyz",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "zyxwvutsrqponmlkjihgfedcba",
            "123456789012",
            "0987654321098765",
            "13579135791357913579",
            "24680246802468024680",
            "qwertyuiopasdfghjkl",
            "poiuytrewqlkjhgfdsa",
            "QwErTyUiOpAsDfGhJkL",
            "zxcvbnmasdfghjklqwertyuiop",
            "azertyuiopqsdfghjklm",
            "1qaz2wsx3edc4rfv",
            "abcabcabcabcabc",
            "abcdabcdabcdabcd",
            "xyzxyzxyzxyzxyzxyzxyzxyz",
            "ab12ab12ab12ab12ab12",
            "ñaññaññaññaññaññañ",
        )
        for (pattern in patterns) {
            val bits = PasswordStrength.estimateBits(pattern)
            assertTrue("$pattern scored $bits bits", PasswordStrength.level(bits) < StrengthLevel.FAIR)
            assertFalse("$pattern was accepted as master password", PasswordStrength.isAcceptableMasterPassword(pattern))
        }
        // Real passphrases and generated passwords keep their score.
        assertTrue(PasswordStrength.isAcceptableMasterPassword("tortuga-Violeta-lunes-73"))
        assertTrue(PasswordStrength.isAcceptableMasterPassword("correcto caballo bateria grapa"))
        assertTrue(PasswordStrength.level(PasswordStrength.estimateBits("Mi gato se llama Pelusa y come 3 veces")) >= StrengthLevel.STRONG)
        repeat(100) {
            val generated = PasswordGenerator.generate(GeneratorOptions(length = 16))
            assertTrue(generated, PasswordStrength.isAcceptableMasterPassword(generated))
        }
        // A sequence costs more than its first step: "abc" is worth less than "abz".
        assertTrue(PasswordStrength.estimateBits("abcdef") < PasswordStrength.estimateBits("abzdef"))
        assertTrue(PasswordStrength.estimateBits("qwerty-") < PasswordStrength.estimateBits("qwzrty-"))
    }
}
