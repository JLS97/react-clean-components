package io.github.jls97.boveda.core.vault

/** One saved login. `toString` never includes secrets, so an entry can't leak through logs. */
data class VaultEntry(
    val id: String,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    /** Apps (`android:<package>`) and sites (`web:<domain>`) this entry was chosen for when autofilling. */
    val autofillTargets: List<String> = emptyList(),
) {
    override fun toString() = "VaultEntry(id=$id)"
}

/** Preferences stored inside the encrypted vault, so nobody can weaken them from outside. */
data class VaultSettings(
    /** Seconds without interaction before locking. 0 locks as soon as the app leaves the screen. */
    val autoLockSeconds: Int = DEFAULT_AUTO_LOCK_SECONDS,
    val clipboardClearSeconds: Int = DEFAULT_CLIPBOARD_CLEAR_SECONDS,
) {
    companion object {
        const val DEFAULT_AUTO_LOCK_SECONDS = 60
        const val DEFAULT_CLIPBOARD_CLEAR_SECONDS = 30
        val AUTO_LOCK_CHOICES = listOf(0, 30, 60, 300, 900)
        val CLIPBOARD_CLEAR_CHOICES = listOf(15, 30, 60, 120)
    }
}

data class VaultData(
    val settings: VaultSettings = VaultSettings(),
    val entries: List<VaultEntry> = emptyList(),
) {
    override fun toString() = "VaultData(entries=${entries.size})"
}

open class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

class WrongPasswordException : VaultException("Wrong master password")

class CorruptedVaultException(message: String, cause: Throwable? = null) : VaultException(message, cause)

class UnsupportedVaultException(message: String) : VaultException(message)

/** The device layer could not be opened: the file was not sealed with this device's key. */
class DeviceBindingException(message: String, cause: Throwable? = null) : VaultException(message, cause)
