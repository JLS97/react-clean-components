package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.KdfParams

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
    /** 2FA secret, sealed under the 2FA key: only a fingerprint (or the recovery code) opens it. */
    val otp: SealedOtp? = null,
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
    /** Present once the first 2FA code has been saved. */
    val otpKeyring: OtpKeyring? = null,
) {
    override fun toString() = "VaultData(entries=${entries.size})"
}

/**
 * The 2FA secret of one entry, encrypted with the 2FA key (see `core.otp.OtpCrypto`). Opening the
 * vault is not enough to read it.
 */
class SealedOtp(bytes: ByteArray) {
    private val data = bytes.copyOf()

    val bytes: ByteArray get() = data.copyOf()

    override fun equals(other: Any?) = other is SealedOtp && data.contentEquals(other.data)

    override fun hashCode() = data.contentHashCode()

    override fun toString() = "SealedOtp"
}

/**
 * The 2FA key wrapped with a key derived from the recovery code. It travels inside the vault, and
 * so inside every backup, which is how the codes are recovered on another phone. Day to day the
 * phone uses its own copy of the 2FA key, wrapped by a Keystore key that needs a fingerprint.
 */
class OtpKeyring(id: ByteArray, val kdfParams: KdfParams, salt: ByteArray, wrappedKey: ByteArray) {
    private val idBytes = id.copyOf()
    private val saltBytes = salt.copyOf()
    private val wrappedKeyBytes = wrappedKey.copyOf()

    /** Random identifier that ties this phone's copy of the 2FA key to this keyring. */
    val id: ByteArray get() = idBytes.copyOf()
    val salt: ByteArray get() = saltBytes.copyOf()
    val wrappedKey: ByteArray get() = wrappedKeyBytes.copyOf()

    override fun equals(other: Any?) = other is OtpKeyring &&
        idBytes.contentEquals(other.idBytes) &&
        kdfParams == other.kdfParams &&
        saltBytes.contentEquals(other.saltBytes) &&
        wrappedKeyBytes.contentEquals(other.wrappedKeyBytes)

    override fun hashCode() = idBytes.contentHashCode()

    override fun toString() = "OtpKeyring"

    companion object {
        const val ID_SIZE = 16
        const val KEY_SIZE = 32
    }
}

open class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

class WrongPasswordException : VaultException("Wrong master password")

class CorruptedVaultException(message: String, cause: Throwable? = null) : VaultException(message, cause)

class UnsupportedVaultException(message: String) : VaultException(message)

/** The device layer could not be opened: the file was not sealed with this device's key. */
class DeviceBindingException(message: String, cause: Throwable? = null) : VaultException(message, cause)
