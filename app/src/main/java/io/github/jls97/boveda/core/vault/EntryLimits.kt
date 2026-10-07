package io.github.jls97.boveda.core.vault

/**
 * How much a person may type into each field of an entry, in UTF-8 bytes. These are the limits
 * the interface enforces before anything reaches the vault; they are well below the format
 * ceilings of [VaultCodec] (`MAX_*_BYTES`), which only keep crafted files from exhausting the
 * phone and which older vaults, written when there was no limit, may still exceed.
 *
 * Checked in bytes rather than characters because the codec stores bytes: a title of 1 024 «ñ»
 * takes 2 048 bytes.
 */
object EntryLimits {
    const val TITLE_BYTES = 1_024
    const val USERNAME_BYTES = 1_024
    const val PASSWORD_BYTES = 4_096
    const val URL_BYTES = 2_048
    const val NOTES_BYTES = 65_536

    /** Size of [value] in UTF-8, counted without copying it (it may be a password). */
    fun utf8Size(value: String): Int {
        var bytes = 0
        var i = 0
        while (i < value.length) {
            val codePoint = value.codePointAt(i)
            bytes += when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            i += Character.charCount(codePoint)
        }
        return bytes
    }

    fun fits(value: String, maxBytes: Int): Boolean = utf8Size(value) <= maxBytes

    /** Messages for the person, or null when the field fits. */
    fun titleError(value: String): String? =
        if (fits(value, TITLE_BYTES)) null else "El nombre supera 1 KB; recórtalo."

    fun usernameError(value: String): String? =
        if (fits(value, USERNAME_BYTES)) null else "El usuario supera 1 KB; recórtalo."

    fun passwordError(value: String): String? =
        if (fits(value, PASSWORD_BYTES)) null else "La contraseña supera 4 KB; recórtala."

    fun urlError(value: String): String? =
        if (fits(value, URL_BYTES)) null else "La dirección supera 2 KB; recórtala."

    fun notesError(value: String): String? =
        if (fits(value, NOTES_BYTES)) null else "Las notas superan 64 KB; recórtalas."

    /** The message for the first field of [entry] above its limit, or null when it can be saved. */
    fun oversizedField(entry: VaultEntry): String? =
        titleError(entry.title)
            ?: usernameError(entry.username)
            ?: passwordError(entry.password)
            ?: urlError(entry.url)
            ?: notesError(entry.notes)
}
