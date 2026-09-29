package io.github.jls97.boveda.core.generator

import io.github.jls97.boveda.core.crypto.secureRandom
import io.github.jls97.boveda.core.crypto.wipe
import java.security.SecureRandom
import kotlin.math.log2

data class GeneratorOptions(
    val length: Int = 24,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = false,
) {
    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 128
    }
}

/**
 * Random passwords from a CSPRNG. `SecureRandom.nextInt(bound)` is unbiased, every selected
 * character class appears at least once, and a Fisher–Yates shuffle hides where those forced
 * characters ended up.
 */
object PasswordGenerator {
    const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGITS = "0123456789"

    /** Printable ASCII symbols without quotes, backslash or space, which some sites reject. */
    const val SYMBOLS = "!#\$%&()*+,-./:;<=>?@[]^_{|}~"

    /** Characters that are easy to confuse when read or typed by hand. */
    const val AMBIGUOUS = "Il1O0o|"

    fun characterClasses(options: GeneratorOptions): List<String> = buildList {
        if (options.lowercase) add(LOWERCASE)
        if (options.uppercase) add(UPPERCASE)
        if (options.digits) add(DIGITS)
        if (options.symbols) add(SYMBOLS)
    }.map { set ->
        if (options.avoidAmbiguous) set.filterNot { it in AMBIGUOUS } else set
    }

    fun canGenerate(options: GeneratorOptions): Boolean {
        val classes = characterClasses(options)
        return classes.isNotEmpty() &&
            options.length in GeneratorOptions.MIN_LENGTH..GeneratorOptions.MAX_LENGTH &&
            options.length >= classes.size
    }

    fun generate(options: GeneratorOptions, random: SecureRandom = secureRandom): String {
        require(canGenerate(options)) { "Invalid generator options" }
        val classes = characterClasses(options)
        val pool = classes.joinToString(separator = "")
        val chars = CharArray(options.length)
        classes.forEachIndexed { index, set -> chars[index] = set[random.nextInt(set.length)] }
        for (index in classes.size until chars.size) chars[index] = pool[random.nextInt(pool.length)]
        for (index in chars.size - 1 downTo 1) {
            val other = random.nextInt(index + 1)
            val swap = chars[index]
            chars[index] = chars[other]
            chars[other] = swap
        }
        return String(chars).also { chars.wipe() }
    }

    /** Entropy of a generated password in bits (slightly generous: ignores the forced classes). */
    fun entropyBits(options: GeneratorOptions): Double {
        val poolSize = characterClasses(options).sumOf { it.length }
        return if (poolSize == 0) 0.0 else options.length * log2(poolSize.toDouble())
    }
}
