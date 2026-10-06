package io.github.jls97.boveda.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import io.github.jls97.boveda.core.crypto.wipe
import io.github.jls97.boveda.core.otp.OtpCrypto
import io.github.jls97.boveda.core.vault.OtpKeyring
import io.github.jls97.boveda.data.readFile
import io.github.jls97.boveda.data.writeFile
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.Cipher

/**
 * This phone's copy of the 2FA key, wrapped by a Keystore key that requires a strong (class 3)
 * biometric for every single use. The system destroys that Keystore key when a fingerprint is
 * added or the screen lock is removed; the codes then come back with the recovery code.
 *
 * File: `"BVOK" | slot u8 | keyring id (16) | IV (12) | wrapped 2FA key (32 + 16)`. The keyring
 * id says which vault the copy belongs to, so a restored backup never uses a stale one. The slot
 * (1 or 2) says which of two Keystore aliases wraps the copy: they alternate, so enrolling a new
 * key (first code, recovery, or a new 2FA key with a new recovery code) never destroys the key the
 * current copy still needs until the new copy is written. Files written before the slots existed
 * carry a 1 there (their format version), which is slot 1.
 */
internal class OtpKeyManager(private val keys: KeystoreKeys, private val file: File) {

    private class Stored(val slot: Byte, val keyringId: ByteArray, val iv: ByteArray, val wrappedKey: ByteArray) {
        val alias: String get() = aliasOf(slot)
    }

    /** Slot of the key [enrollmentCipher] created, until [finishEnrollment] writes its copy. */
    @Volatile
    private var enrollingSlot: Byte? = null

    /** True if this phone holds a copy of the 2FA key of [keyringId] (it may still be invalidated). */
    fun isReadyFor(keyringId: ByteArray): Boolean {
        val stored = read() ?: return false
        return stored.keyringId.contentEquals(keyringId) && keys.get(stored.alias) != null
    }

    /**
     * Cipher of a new fingerprint key, to authorize with BiometricPrompt before [finishEnrollment].
     * The key goes to the slot the current copy does not use, so a cipher from [unlockCipher] keeps
     * working meanwhile.
     */
    fun enrollmentCipher(): Cipher {
        val slot: Byte = if (read()?.slot == SLOT_A) SLOT_B else SLOT_A
        val key = keys.create(aliasOf(slot)) {
            setUserAuthenticationRequired(true)
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            setInvalidatedByBiometricEnrollment(true)
        }
        enrollingSlot = slot
        return KeystoreKeys.encryptCipher(key)
    }

    /** Writes the copy wrapped by [authorizedCipher] (from [enrollmentCipher]) and drops the previous key. */
    fun finishEnrollment(authorizedCipher: Cipher, keyringId: ByteArray, otpKey: ByteArray) {
        val slot = enrollingSlot ?: throw GeneralSecurityException("No 2FA key enrollment in progress")
        authorizedCipher.updateAAD(OtpCrypto.deviceAad(keyringId))
        val wrapped = authorizedCipher.doFinal(otpKey)
        val iv = authorizedCipher.iv
        if (iv.size != KeystoreKeys.IV_SIZE || wrapped.size != WRAPPED_SIZE) {
            throw GeneralSecurityException("Unexpected Keystore output")
        }
        writeFile(file, MAGIC + byteArrayOf(slot) + keyringId + iv + wrapped)
        enrollingSlot = null
        keys.delete(aliasOf(if (slot == SLOT_A) SLOT_B else SLOT_A))
    }

    /**
     * Cipher to authorize with BiometricPrompt before [unwrap], or null when this phone has no
     * usable copy of the 2FA key of [keyringId]. A copy the system invalidated is deleted.
     */
    fun unlockCipher(keyringId: ByteArray): Cipher? {
        val stored = read()?.takeIf { it.keyringId.contentEquals(keyringId) } ?: return null
        val key = keys.get(stored.alias) ?: return null
        return try {
            KeystoreKeys.decryptCipher(key, stored.iv)
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable()
            null
        }
    }

    /** The 2FA key. The caller wipes it. */
    fun unwrap(authorizedCipher: Cipher, keyringId: ByteArray): ByteArray {
        val stored = read() ?: throw IOException("The 2FA key of this phone is missing")
        if (!stored.keyringId.contentEquals(keyringId)) throw GeneralSecurityException("2FA key of another vault")
        authorizedCipher.updateAAD(OtpCrypto.deviceAad(keyringId))
        val otpKey = authorizedCipher.doFinal(stored.wrappedKey)
        if (otpKey.size != OtpKeyring.KEY_SIZE) {
            otpKey.wipe()
            throw GeneralSecurityException("Invalid 2FA key")
        }
        return otpKey
    }

    fun disable() {
        keys.delete(aliasOf(SLOT_A))
        keys.delete(aliasOf(SLOT_B))
        file.delete()
    }

    private fun read(): Stored? {
        if (!file.exists()) return null
        val bytes = try {
            readFile(file)
        } catch (e: IOException) {
            return null
        }
        val slot = bytes.getOrNull(MAGIC.size)
        if (bytes.size != FILE_SIZE || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) ||
            (slot != SLOT_A && slot != SLOT_B)
        ) {
            return null
        }
        var offset = MAGIC.size + 1
        fun next(size: Int) = bytes.copyOfRange(offset, offset + size).also { offset += size }
        return Stored(slot, next(OtpKeyring.ID_SIZE), next(KeystoreKeys.IV_SIZE), next(WRAPPED_SIZE))
    }

    private companion object {
        /** Slot 1 keeps the alias of the first format, so copies written before the slots keep opening. */
        const val SLOT_A: Byte = 1
        const val SLOT_B: Byte = 2

        fun aliasOf(slot: Byte): String = if (slot == SLOT_A) "boveda.otp.v1" else "boveda.otp.v1.b"

        val MAGIC = byteArrayOf(0x42, 0x56, 0x4F, 0x4B) // "BVOK"
        const val WRAPPED_SIZE = OtpKeyring.KEY_SIZE + 16
        val FILE_SIZE = MAGIC.size + 1 + OtpKeyring.ID_SIZE + KeystoreKeys.IV_SIZE + WRAPPED_SIZE
    }
}
