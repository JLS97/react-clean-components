package io.github.jls97.boveda.core.generator

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.min

enum class StrengthLevel { VERY_WEAK, WEAK, FAIR, STRONG, VERY_STRONG }

/**
 * Rough strength estimate for passwords a person types, such as the master password. It counts
 * the character pool, gives less credit to repeated or consecutive characters (by code or along a
 * keyboard row, less and less as the run goes on), caps the score of a password made of a few
 * repeated fragments, and halves it when the password contains a well-known weak fragment. It is
 * a guide, not a guarantee.
 */
object PasswordStrength {
    const val MASTER_MIN_LENGTH = 12

    private val COMMON_FRAGMENTS = listOf(
        "password", "contraseña", "contrasena", "qwerty", "asdf", "123456", "654321", "abc123",
        "111111", "000000", "iloveyou", "teamo", "admin", "letmein", "welcome", "bienvenido",
        "dragon", "monkey", "football", "futbol",
    )

    /** Rows and columns of the QWERTY (with ñ) and AZERTY layouts; digits are consecutive codes already. */
    private val KEYBOARD_SEQUENCES = listOf(
        "qwertyuiop", "asdfghjklñ", "zxcvbnm", "azertyuiop", "qsdfghjklm", "wxcvbn",
        "1qaz", "2wsx", "3edc", "4rfv", "5tgb", "6yhn", "7ujm", "8ik", "9ol", "0p",
    )

    fun estimateBits(password: CharSequence): Double {
        if (password.isEmpty()) return 0.0
        var poolSize = 0
        if (password.any { it in 'a'..'z' }) poolSize += 26
        if (password.any { it in 'A'..'Z' }) poolSize += 26
        if (password.any { it in '0'..'9' }) poolSize += 10
        if (password.any { it.code in 32..126 && !it.isLetterOrDigit() }) poolSize += 33
        if (password.any { it.code > 126 }) poolSize += 64
        val poolBits = log2(poolSize.coerceAtLeast(2).toDouble())

        var effectiveLength = 0.0
        var run = 0 // consecutive sequence steps so far: "abc" is 2, "abcdef" is 5
        for (index in password.indices) {
            val current = password[index]
            val previous = if (index > 0) password[index - 1] else null
            val step = previous != null && current != previous && isSequenceStep(previous, current)
            run = if (step) run + 1 else 0
            effectiveLength += when {
                previous == null -> 1.0
                current == previous -> 0.25
                step -> if (run == 1) 0.5 else 0.25
                else -> 1.0
            }
        }

        var bits = effectiveLength * poolBits
        // A password that repeats a short pattern ("abcabcabc", "1357913579") only has the
        // pattern's worth of distinct pairs, however long it is.
        val lower = password.toString().lowercase()
        bits = min(bits, (distinctBigrams(lower) + 1) * poolBits)
        if (COMMON_FRAGMENTS.any { it in lower }) bits /= 2
        return bits
    }

    /** True if [current] follows [previous] in the alphabet, the digits, or along a keyboard row, either way. */
    private fun isSequenceStep(previous: Char, current: Char): Boolean {
        val from = previous.lowercaseChar()
        val to = current.lowercaseChar()
        if (abs(to.code - from.code) == 1) return true
        return KEYBOARD_SEQUENCES.any { row ->
            val fromIndex = row.indexOf(from)
            val toIndex = row.indexOf(to)
            fromIndex >= 0 && toIndex >= 0 && abs(toIndex - fromIndex) == 1
        }
    }

    private fun distinctBigrams(lower: String): Int {
        val seen = HashSet<String>()
        for (index in 1 until lower.length) seen += lower.substring(index - 1, index + 1)
        return seen.size
    }

    fun level(bits: Double): StrengthLevel = when {
        bits < 40 -> StrengthLevel.VERY_WEAK
        bits < 60 -> StrengthLevel.WEAK
        bits < 80 -> StrengthLevel.FAIR
        bits < 110 -> StrengthLevel.STRONG
        else -> StrengthLevel.VERY_STRONG
    }

    /** A master password must be long and at least [StrengthLevel.FAIR]. */
    fun isAcceptableMasterPassword(password: CharSequence): Boolean =
        password.length >= MASTER_MIN_LENGTH && level(estimateBits(password)) >= StrengthLevel.FAIR
}
