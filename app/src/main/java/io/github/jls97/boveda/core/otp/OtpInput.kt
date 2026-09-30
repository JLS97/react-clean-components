package io.github.jls97.boveda.core.otp

import io.github.jls97.boveda.core.crypto.wipe
import java.io.ByteArrayOutputStream

/**
 * Everything needed to compute the codes of one account. It only exists in memory, for as long as
 * a code is on screen or being saved; [wipe] it afterwards.
 */
class OtpSecret(key: ByteArray, val params: OtpParams, issuer: String = "", account: String = "") {
    private val keyBytes = key.copyOf()

    val issuer = issuer.trim().take(MAX_LABEL_LENGTH)
    val account = account.trim().take(MAX_LABEL_LENGTH)

    /** A copy of the secret key, to seal it. The caller wipes it. */
    val key: ByteArray get() = keyBytes.copyOf()

    /** "Google · tu@gmail.com", or empty when the link didn't say. */
    val label: String get() = listOf(issuer, account).filter { it.isNotEmpty() }.joinToString(" · ")

    fun code(epochMillis: Long): String = Totp.code(keyBytes, params, epochMillis)

    fun secondsLeft(epochMillis: Long): Int = Totp.secondsLeft(params, epochMillis)

    fun wipe() = keyBytes.wipe()

    override fun equals(other: Any?) = other is OtpSecret &&
        keyBytes.contentEquals(other.keyBytes) &&
        params == other.params &&
        issuer == other.issuer &&
        account == other.account

    override fun hashCode() = keyBytes.contentHashCode()

    override fun toString() = "OtpSecret(${params.algorithm}, ${params.digits} digits)"

    companion object {
        const val MAX_LABEL_LENGTH = 200
    }
}

enum class OtpInputError {
    EMPTY,

    /** An otpauth link for counter-based codes (HOTP), which Bóveda doesn't handle. */
    NOT_TIME_BASED,

    /** The "transfer accounts" QR of Google Authenticator. */
    MIGRATION_EXPORT,

    /** A link, but not an otpauth one (a web address, for example). */
    NOT_OTPAUTH,
    MISSING_SECRET,
    INVALID_SECRET,
    SECRET_TOO_SHORT,
    UNSUPPORTED_ALGORITHM,
    INVALID_DIGITS,
    INVALID_PERIOD,
}

sealed interface OtpInputResult {
    class Valid(val secret: OtpSecret) : OtpInputResult

    data class Invalid(val error: OtpInputError) : OtpInputResult
}

/**
 * Reads what a service gives when turning on 2FA: the `otpauth://totp/...` link inside its QR
 * code, or the secret key shown next to it for typing by hand.
 */
object OtpInput {
    /** 80 bits, the shortest key Google Authenticator accepts. Anything shorter is a typo. */
    const val MIN_SECRET_BYTES = 10
    const val MAX_SECRET_BYTES = 128

    private const val SCHEME = "otpauth://"
    private const val MIGRATION_SCHEME = "otpauth-migration://"

    /** Parses a link, or a bare secret key that uses [manualParams]. */
    fun parse(text: String, manualParams: OtpParams = OtpParams.DEFAULT): OtpInputResult {
        val input = text.trim()
        return when {
            input.isEmpty() -> OtpInputResult.Invalid(OtpInputError.EMPTY)
            input.startsWith(SCHEME, ignoreCase = true) -> parseLink(input.substring(SCHEME.length))
            input.startsWith(MIGRATION_SCHEME, ignoreCase = true) -> OtpInputResult.Invalid(OtpInputError.MIGRATION_EXPORT)
            "://" in input -> OtpInputResult.Invalid(OtpInputError.NOT_OTPAUTH)
            else -> secretResult(input, manualParams, issuer = "", account = "")
        }
    }

    /** `totp/Issuer:account?secret=...&issuer=...&algorithm=...&digits=...&period=...` */
    private fun parseLink(rest: String): OtpInputResult {
        val path = rest.substringBefore('?')
        val query = rest.substringAfter('?', missingDelimiterValue = "")
        if (!path.substringBefore('/').equals("totp", ignoreCase = true)) {
            return OtpInputResult.Invalid(OtpInputError.NOT_TIME_BASED)
        }
        val label = percentDecode(path.substringAfter('/', missingDelimiterValue = ""))
        val parameters = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val name = percentDecode(pair.substringBefore('=')).lowercase()
            if (name !in parameters) parameters[name] = percentDecode(pair.substringAfter('=', ""))
        }

        val algorithm = parameters["algorithm"]?.let {
            OtpAlgorithm.fromName(it) ?: return OtpInputResult.Invalid(OtpInputError.UNSUPPORTED_ALGORITHM)
        } ?: OtpAlgorithm.SHA1
        val digits = parameters["digits"]?.let {
            it.trim().toIntOrNull()?.takeIf { value -> value in OtpParams.DIGITS }
                ?: return OtpInputResult.Invalid(OtpInputError.INVALID_DIGITS)
        } ?: 6
        val period = parameters["period"]?.let {
            it.trim().toIntOrNull()?.takeIf { value -> value in OtpParams.PERIOD_SECONDS }
                ?: return OtpInputResult.Invalid(OtpInputError.INVALID_PERIOD)
        } ?: 30

        // The label is "Issuer:account" or just "account"; the issuer parameter wins if present.
        val labelIssuer = if (':' in label) label.substringBefore(':') else ""
        val account = if (':' in label) label.substringAfter(':') else label
        val issuer = parameters["issuer"]?.takeIf { it.isNotBlank() } ?: labelIssuer

        val secret = parameters["secret"] ?: return OtpInputResult.Invalid(OtpInputError.MISSING_SECRET)
        return secretResult(secret, OtpParams(algorithm, digits, period), issuer, account)
    }

    private fun secretResult(encoded: String, params: OtpParams, issuer: String, account: String): OtpInputResult {
        if (encoded.isBlank()) return OtpInputResult.Invalid(OtpInputError.MISSING_SECRET)
        val key = Base32.decode(encoded) ?: return OtpInputResult.Invalid(OtpInputError.INVALID_SECRET)
        try {
            return when {
                key.size < MIN_SECRET_BYTES -> OtpInputResult.Invalid(OtpInputError.SECRET_TOO_SHORT)
                key.size > MAX_SECRET_BYTES -> OtpInputResult.Invalid(OtpInputError.INVALID_SECRET)
                else -> OtpInputResult.Valid(OtpSecret(key, params, issuer, account))
            }
        } finally {
            key.wipe()
        }
    }

    /** RFC 3986 percent-decoding. A `+` stays a `+`: emails in labels can contain one. */
    private fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val bytes = ByteArrayOutputStream(value.length)
        var literalStart = 0
        var index = 0
        while (index < value.length) {
            val high = if (value[index] == '%' && index + 2 < value.length) Character.digit(value[index + 1], 16) else -1
            val low = if (high >= 0) Character.digit(value[index + 2], 16) else -1
            if (high >= 0 && low >= 0) {
                bytes.write(value.substring(literalStart, index).toByteArray(Charsets.UTF_8))
                bytes.write((high shl 4) or low)
                index += 3
                literalStart = index
            } else {
                index++
            }
        }
        bytes.write(value.substring(literalStart).toByteArray(Charsets.UTF_8))
        return bytes.toString(Charsets.UTF_8.name())
    }
}
