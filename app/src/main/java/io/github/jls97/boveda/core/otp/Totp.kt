package io.github.jls97.boveda.core.otp

import io.github.jls97.boveda.core.crypto.wipe
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC used to compute the codes. [id] is what gets persisted inside every sealed 2FA secret, so
 * it is explicit and stable: adding, removing or reordering constants must never change the
 * meaning of a record that is already stored. Never reuse an id.
 */
enum class OtpAlgorithm(val id: Int, val macName: String, val label: String) {
    SHA1(1, "HmacSHA1", "SHA-1"),
    SHA256(2, "HmacSHA256", "SHA-256"),
    SHA512(3, "HmacSHA512", "SHA-512"),
    ;

    companion object {
        /** Accepts "SHA1", "sha256", "SHA-512"... as written in otpauth links. */
        fun fromName(name: String): OtpAlgorithm? {
            val normalized = name.replace("-", "").trim()
            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        }

        /** The algorithm persisted with [id], or null if no version of the app has defined it. */
        fun fromId(id: Int): OtpAlgorithm? = entries.firstOrNull { it.id == id }
    }
}

/** How codes are computed. The defaults are what nearly every service uses. */
data class OtpParams(
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
) {
    init {
        require(digits in DIGITS) { "Unsupported number of digits" }
        require(period in PERIOD_SECONDS) { "Unsupported period" }
    }

    companion object {
        // Before DEFAULT: its constructor checks these ranges.
        val DIGITS = 6..8
        val PERIOD_SECONDS = 10..300
        val DEFAULT = OtpParams()
    }
}

/** Time-based one-time passwords (RFC 6238), built on HOTP (RFC 4226). */
object Totp {
    private val POWERS_OF_TEN = intArrayOf(1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000)

    /** The code valid at [epochMillis]. */
    fun code(secret: ByteArray, params: OtpParams, epochMillis: Long): String =
        hotp(secret, counter(params, epochMillis), params.algorithm, params.digits)

    /** Number of whole periods since 1970, the counter the code is computed from. */
    fun counter(params: OtpParams, epochMillis: Long): Long =
        Math.floorDiv(Math.floorDiv(epochMillis, 1_000L), params.period.toLong())

    /** Seconds until the code shown at [epochMillis] changes, from `period` down to 1. */
    fun secondsLeft(params: OtpParams, epochMillis: Long): Int =
        params.period - Math.floorMod(Math.floorDiv(epochMillis, 1_000L), params.period.toLong()).toInt()

    /** RFC 4226: HMAC of the counter, dynamic truncation, then the last [digits] decimal digits. */
    fun hotp(secret: ByteArray, counter: Long, algorithm: OtpAlgorithm, digits: Int): String {
        require(digits in OtpParams.DIGITS) { "Unsupported number of digits" }
        val mac = Mac.getInstance(algorithm.macName)
        mac.init(SecretKeySpec(secret, algorithm.macName))
        val message = ByteArray(8) { index -> (counter ushr (56 - 8 * index)).toByte() }
        val hash = mac.doFinal(message)
        try {
            val offset = hash[hash.size - 1].toInt() and 0x0F
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
            return (binary % POWERS_OF_TEN[digits]).toString().padStart(digits, '0')
        } finally {
            hash.wipe()
        }
    }

    /** "123 456": two groups, so the code is easy to read and type. */
    fun format(code: String): String {
        if (code.length < 6) return code
        val split = code.length / 2
        return code.substring(0, split) + " " + code.substring(split)
    }
}
