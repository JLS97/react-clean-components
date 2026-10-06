package io.github.jls97.boveda.core.autofill

/**
 * A classified field, in screen order. [T] is the platform id (AutofillId on Android).
 * [webDomain] and [webScheme] are the ones of the nearest ancestor that declared them (null in
 * native apps): fields of a frame with another origin must never be filled together.
 */
data class DetectedField<T>(val id: T, val kind: FieldKind, val webDomain: String? = null, val webScheme: String? = null) {
    /** Host efectivo, o el dominio tal cual (en minúsculas) si no es un host válido. */
    val host: String? get() = webDomain?.let { Domains.host(it) ?: it.trim().lowercase() }
}

/** The fields of a login, sign-up or 2FA form that Bóveda cares about. */
data class LoginFields<T>(
    val username: T?,
    val password: T?,
    val newPasswords: List<T>,
    /** Where the 2FA code goes. It is filled on its own, after a fingerprint. */
    val otp: T? = null,
) {
    /** Fields that can be filled from a saved entry. */
    val fillIds: List<T> get() = listOfNotNull(username, password)

    /** Every field this form touches, filled or saved. */
    val allIds: List<T> get() = fillIds + newPasswords + listOfNotNull(otp)
}

object FieldSelection {
    /**
     * Picks the username, password and 2FA code fields of a form, or null if it has none.
     * When no field declares itself as a username, the text field right before the first
     * password field is used, which is how almost every login form is laid out.
     *
     * Every field returned shares the same effective host. On a web page only the fields of
     * [mainWebDomain], the main window's document, are kept: an iframe of another site or the
     * browser's own address bar never receive its credentials. If none remains, nothing is filled.
     */
    fun <T> select(fields: List<DetectedField<T>>, mainWebDomain: String? = null): LoginFields<T>? {
        val coherent = sameOrigin(fields, mainWebDomain)
        val selected = selectFrom(coherent) ?: return null
        val chosen = coherent.filter { it.id in selected.allIds }
        // Belt and braces: whatever was selected must come from a single origin.
        return if (chosen.map { it.host }.distinct().size > 1) null else selected
    }

    /** Fields of the main document's origin; in a native app (no field has a domain) all of them. */
    fun <T> sameOrigin(fields: List<DetectedField<T>>, mainWebDomain: String?): List<DetectedField<T>> {
        if (fields.all { it.webDomain == null }) return fields
        val mainHost = mainWebDomain?.let { Domains.host(it) ?: it.trim().lowercase() }
        return fields.filter { it.host == mainHost }
    }

    private fun <T> selectFrom(fields: List<DetectedField<T>>): LoginFields<T>? {
        val passwordIndex = fields.indexOfFirst { it.kind == FieldKind.PASSWORD }
        val newPasswords = fields.filter { it.kind == FieldKind.NEW_PASSWORD }.map { it.id }
        val firstSecretIndex = listOf(passwordIndex, fields.indexOfFirst { it.kind == FieldKind.NEW_PASSWORD })
            .filter { it >= 0 }
            .minOrNull()
        val username = fields.firstOrNull { it.kind == FieldKind.USERNAME }?.id
            ?: firstSecretIndex?.let { index ->
                fields.subList(0, index).lastOrNull { it.kind == FieldKind.OTHER_TEXT }?.id
            }
        val password = fields.getOrNull(passwordIndex)?.id
        val otp = fields.firstOrNull { it.kind == FieldKind.OTP }?.id
        if (username == null && password == null && newPasswords.isEmpty() && otp == null) return null
        return LoginFields(username, password, newPasswords, otp)
    }
}
