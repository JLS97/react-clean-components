package io.github.jls97.boveda.core.autofill

/**
 * The screen of another app as Bóveda sees it. [packageName] comes from the system and can be
 * trusted; [reportedWebDomain] is whatever the app put in its views, so it is only believed after
 * `AppSigners.resolveTarget` checks that the app is a verified browser.
 *
 * Genérica en el tipo de id ([T] es `AutofillId` en la app y un `String` en los tests) para que
 * la elección del dominio y del esquema que viajan a la pantalla de autorrelleno se pruebe en la
 * JVM sobre árboles sintéticos, y no solo por lectura.
 */
class ParsedStructure<T>(
    val packageName: String,
    private val walked: WalkedStructure<T>,
) {
    private val fields: List<WalkedField<T>> get() = walked.fields

    val login: LoginFields<T>? = FieldSelection.select(fields.map { it.field }, walked.mainWebDomain)

    private val filled: List<WalkedField<T>> =
        login?.let { chosen -> fields.filter { it.field.id in chosen.allIds } }.orEmpty()

    /**
     * Domain of the fields that are going to be filled or saved (they all share it), not the first
     * one of the tree: what the user is shown is where the data ends up.
     */
    val reportedWebDomain: String? = filled.firstNotNullOfOrNull { it.field.webDomain }

    /** Scheme of those fields; an unencrypted one wins so the warning is never hidden. */
    val reportedWebScheme: String? =
        filled.firstOrNull { TargetResolver.isUnencrypted(it.field.webScheme) }?.field?.webScheme
            ?: filled.firstNotNullOfOrNull { it.field.webScheme }

    /** Current text of a field; only present in save requests. */
    fun textOf(id: T?): String? = if (id == null) null else fields.firstOrNull { it.field.id == id }?.text
}
