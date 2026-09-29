package io.github.jls97.boveda.core.generator

import kotlin.math.abs
import kotlin.math.log2

enum class StrengthLevel { VERY_WEAK, WEAK, FAIR, STRONG, VERY_STRONG }

/**
 * Rough strength estimate for passwords a person types, such as the master password. It counts
 * the character pool, gives less credit to repeated or consecutive characters, and halves the
 * score when the password contains a well-known weak fragment. It is a guide, not a guarantee.
 */
object PasswordStrength {
    const val MASTER_MIN_LENGTH = 12

    private val COMMON_FRAGMENTS = listOf(
        "password", "contraseña", "contrasena", "qwerty", "asdf", "123456", "654321", "abc123",
        "111111", "000000", "iloveyou", "teamo", "admin", "letmein", "welcome", "bienvenido",
        "dragon", "monkey", "football", "futbol",
    )

    fun estimateBits(password: CharSequence): Double {
        if (password.isEmpty()) return 0.0
        var poolSize = 0
        if (password.any { it in 'a'..'z' }) poolSize += 26
        if (password.any { it in 'A'..'Z' }) poolSize += 26
        if (password.any { it in '0'..'9' }) poolSize += 10
        if (password.any { it.code in 32..126 && !it.isLetterOrDigit() }) poolSize += 33
        if (password.any { it.code > 126 }) poolSize += 64

        var effectiveLength = 0.0
        for (index in password.indices) {
            val current = password[index]
            val previous = if (index > 0) password[index - 1] else null
            effectiveLength += when {
                previous == null -> 1.0
                current == previous -> 0.25
                abs(current.code - previous.code) == 1 -> 0.5
                else -> 1.0
            }
        }

        var bits = effectiveLength * log2(poolSize.coerceAtLeast(2).toDouble())
        val lower = password.toString().lowercase()
        if (COMMON_FRAGMENTS.any { it in lower }) bits /= 2
        return bits
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
