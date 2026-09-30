package io.github.jls97.boveda.core.autofill

enum class FieldKind { USERNAME, PASSWORD, NEW_PASSWORD, OTP, OTHER_TEXT, IGNORED }

/** What the keyboard type of a field says. The Android layer derives it from `InputType`. */
enum class InputKind { PASSWORD, EMAIL, TEXT, OTHER }

/** Everything known about one input field of another app or web page. */
data class FieldSignals(
    val autofillHints: List<String> = emptyList(),
    val inputKind: InputKind = InputKind.OTHER,
    val htmlTag: String? = null,
    val htmlAttributes: Map<String, String> = emptyMap(),
    /** Visible hint, resource id name, content description... */
    val texts: List<String> = emptyList(),
)

/**
 * Decides whether a field holds a username (or email), a password, a new password (sign-up or
 * password change), a 2FA code or something else. Signals are checked from most to least reliable: autofill
 * hints declared by the app, HTML attributes from browsers, the keyboard type and finally words
 * in the field's hint or id, in Spanish and English.
 */
object FieldClassifier {
    private val MEANINGLESS_HINTS = setOf("off", "on", "true", "false")
    private val USERNAME_HINTS = setOf("username", "emailaddress", "email", "newusername")
    private val NEW_PASSWORD_HINTS = setOf("newpassword")
    private val NON_TEXT_INPUT_TYPES = setOf(
        "hidden", "submit", "button", "reset", "checkbox", "radio", "file", "image", "range", "color",
        "date", "datetime-local", "month", "week", "time", "search",
    )
    private val HTML_TEXT_ATTRIBUTES = setOf("name", "id", "placeholder", "aria-label", "label", "title")

    private val PASSWORD_WORDS = listOf("password", "passwd", "contraseña", "contrasena", "passcode", "passwort", "senha")
    private val PASSWORD_TOKENS = setOf("pass", "pwd", "pin", "clave")
    private val USER_WORDS = listOf("username", "usuario", "email", "e-mail", "correo", "login", "userid", "account")
    private val USER_TOKENS = setOf(
        "user", "mail", "dni", "nif", "nie", "cuenta", "phone", "telefono", "teléfono", "movil", "móvil", "documento",
    )
    private val OTP_TOKENS = setOf("otp", "totp", "2fa", "mfa", "tfa", "2sv")
    private val OTP_WORDS = listOf(
        "one-time code", "one time code", "onetimecode", "one-time password", "one time password", "onetimepassword",
        "verification code", "verificationcode", "authentication code", "authenticationcode", "authenticator",
        "two-factor", "two factor", "twofactor", "2-step", "2step",
        "código de verificación", "codigo de verificacion", "código de autenticación", "codigo de autenticacion",
        "autenticador", "un solo uso", "dos pasos", "doble factor",
    )
    private val NEW_TOKENS = setOf(
        "new", "nueva", "nuevo", "confirm", "confirmar", "confirmation", "confirmación", "confirmacion",
        "repeat", "repite", "repetir", "retype", "verify", "verificar",
    )

    private val HTML_TEXT_INPUT_TYPES = setOf("", "text", "tel", "number")

    fun classify(signals: FieldSignals): FieldKind {
        fromHints(signals.autofillHints)?.let { return it }
        fromHtml(signals)?.let { return it }
        // Browsers don't always report a keyboard type for web fields: a plain HTML text input is
        // still a text field, so it can be the username before a password field.
        val isHtmlTextInput = signals.htmlTag?.lowercase() == "input" &&
            signals.htmlAttributes["type"]?.trim()?.lowercase().orEmpty() in HTML_TEXT_INPUT_TYPES
        val effective = if (isHtmlTextInput && signals.inputKind == InputKind.OTHER) {
            signals.copy(inputKind = InputKind.TEXT)
        } else {
            signals
        }
        return fromTypeAndWords(effective)
    }

    private fun fromHints(hints: List<String>): FieldKind? {
        val normalized = hints
            .map { hint -> hint.lowercase().filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() && it !in MEANINGLESS_HINTS }
        return when {
            normalized.isEmpty() -> null
            normalized.any { it in NEW_PASSWORD_HINTS } -> FieldKind.NEW_PASSWORD
            normalized.any { it.endsWith("password") } -> FieldKind.PASSWORD
            normalized.any { it in USERNAME_HINTS } -> FieldKind.USERNAME
            // smsOTPCode, emailOTPCode, 2faAppOTPCode (androidx HintConstants), one-time-code...
            normalized.any { "otp" in it || "onetimecode" in it || it.startsWith("2fa") } -> FieldKind.OTP
            else -> null
        }
    }

    private fun fromHtml(signals: FieldSignals): FieldKind? {
        if (signals.htmlTag?.lowercase() != "input") return null
        val type = signals.htmlAttributes["type"]?.trim()?.lowercase().orEmpty()
        val autocomplete = signals.htmlAttributes["autocomplete"]?.lowercase().orEmpty()
        return when {
            type in NON_TEXT_INPUT_TYPES -> FieldKind.IGNORED
            "new-password" in autocomplete -> FieldKind.NEW_PASSWORD
            "one-time-code" in autocomplete -> FieldKind.OTP
            type == "password" || "current-password" in autocomplete -> FieldKind.PASSWORD
            type == "email" || "username" in autocomplete || "email" in autocomplete -> FieldKind.USERNAME
            else -> null
        }
    }

    private fun fromTypeAndWords(signals: FieldSignals): FieldKind {
        val words = signals.texts + signals.htmlAttributes.filterKeys { it.lowercase() in HTML_TEXT_ATTRIBUTES }.values
        val tokens = tokenize(words)
        val text = words.joinToString(" ").lowercase()
        val saysNew = tokens.any { it in NEW_TOKENS }
        // Checked first: "one-time password" or "clave de un solo uso" also look like a password.
        val saysOtp = tokens.any { it in OTP_TOKENS } || OTP_WORDS.any { it in text }
        val saysPassword = tokens.any { it in PASSWORD_TOKENS } || PASSWORD_WORDS.any { it in text }
        val saysUser = tokens.any { it in USER_TOKENS } || USER_WORDS.any { it in text }
        return when (signals.inputKind) {
            InputKind.PASSWORD -> when {
                saysOtp -> FieldKind.OTP
                saysNew -> FieldKind.NEW_PASSWORD
                else -> FieldKind.PASSWORD
            }
            InputKind.EMAIL -> FieldKind.USERNAME
            InputKind.TEXT -> when {
                saysOtp -> FieldKind.OTP
                saysPassword -> if (saysNew) FieldKind.NEW_PASSWORD else FieldKind.PASSWORD
                saysUser -> FieldKind.USERNAME
                else -> FieldKind.OTHER_TEXT
            }
            InputKind.OTHER -> when {
                saysOtp -> FieldKind.OTP
                saysPassword -> FieldKind.PASSWORD
                saysUser -> FieldKind.USERNAME
                else -> FieldKind.IGNORED
            }
        }
    }

    /** Splits `userName`, `user_name` and `user-name` into `user` and `name`. */
    private fun tokenize(values: List<String>): Set<String> = values
        .flatMap { value ->
            value.replace(CAMEL_CASE, "\$1 \$2")
                .lowercase()
                .split(NON_WORD)
                .filter { it.isNotEmpty() }
        }
        .toSet()

    private val CAMEL_CASE = Regex("(\\p{Ll})(\\p{Lu})")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
}
