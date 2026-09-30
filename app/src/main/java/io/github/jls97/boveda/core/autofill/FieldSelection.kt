package io.github.jls97.boveda.core.autofill

/** A classified field, in screen order. [T] is the platform id (AutofillId on Android). */
data class DetectedField<T>(val id: T, val kind: FieldKind)

/** The fields of a login or sign-up form that Bóveda cares about. */
data class LoginFields<T>(
    val username: T?,
    val password: T?,
    val newPasswords: List<T>,
) {
    /** Fields that can be filled from a saved entry. */
    val fillIds: List<T> get() = listOfNotNull(username, password)
}

object FieldSelection {
    /**
     * Picks the username and password fields of a form, or null if it doesn't look like a login.
     * When no field declares itself as a username, the text field right before the first
     * password field is used, which is how almost every login form is laid out.
     */
    fun <T> select(fields: List<DetectedField<T>>): LoginFields<T>? {
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
        if (username == null && password == null && newPasswords.isEmpty()) return null
        return LoginFields(username, password, newPasswords)
    }
}
