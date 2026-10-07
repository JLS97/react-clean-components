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
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher

/**
 * La copia de la clave 2FA de este teléfono, tal y como la usa la sesión. La implementa
 * [OtpKeyManager] sobre el Keystore; los tests JVM de la sesión usan un doble.
 */
interface OtpKeys {
    fun isReadyFor(keyringId: ByteArray): Boolean

    fun enrollmentCipher(): Cipher

    fun finishEnrollment(authorizedCipher: Cipher, keyringId: ByteArray, otpKey: ByteArray)

    fun unlockCipher(keyringId: ByteArray): Cipher?

    fun unwrap(authorizedCipher: Cipher, keyringId: ByteArray): ByteArray

    fun disable()
}

/**
 * This phone's copy of the 2FA key, wrapped by a Keystore key that requires a strong (class 3)
 * biometric for every single use. The system destroys that Keystore key when a fingerprint is
 * added or the screen lock is removed; the codes then come back with the recovery code.
 *
 * The file format is [OtpKeyFile]. The keyring id says which vault the copy belongs to, so a
 * restored backup never uses a stale one. The two slots alternate, so enrolling a new key (first
 * code, recovery, or a new 2FA key with a new recovery code) never destroys the key the current
 * copy still needs until the new copy is written; the key of the other slot is dropped then, or on
 * the next enrollment if that one never finished.
 */
internal class OtpKeyManager(private val keys: KeystoreKeys, private val file: File) : OtpKeys {

    /** Slot of the key [enrollmentCipher] created, until [finishEnrollment] writes its copy. */
    @Volatile
    private var enrollingSlot: Byte? = null

    /** True if this phone holds a copy of the 2FA key of [keyringId] (it may still be invalidated). */
    override fun isReadyFor(keyringId: ByteArray): Boolean {
        val stored = read() ?: return false
        return stored.keyringId.contentEquals(keyringId) && keys.get(stored.alias) != null
    }

    /**
     * Cipher of a new fingerprint key, to authorize with BiometricPrompt before [finishEnrollment].
     * The key goes to the slot the current copy does not use, so a cipher from [unlockCipher] keeps
     * working meanwhile.
     */
    override fun enrollmentCipher(): Cipher {
        val slot = OtpKeyFile.otherSlot(read()?.slot)
        val alias = OtpKeyFile.aliasOf(slot)
        // Whatever is left in the free slot (an enrollment that never finished) is an orphan.
        keys.delete(alias)
        val key = keys.create(alias) {
            setUserAuthenticationRequired(true)
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            setInvalidatedByBiometricEnrollment(true)
        }
        enrollingSlot = slot
        return KeystoreKeys.encryptCipher(key)
    }

    /** Writes the copy wrapped by [authorizedCipher] (from [enrollmentCipher]) and drops the previous key. */
    override fun finishEnrollment(authorizedCipher: Cipher, keyringId: ByteArray, otpKey: ByteArray) {
        val slot = enrollingSlot ?: throw GeneralSecurityException("No 2FA key enrollment in progress")
        authorizedCipher.updateAAD(OtpCrypto.deviceAad(keyringId))
        val wrapped = authorizedCipher.doFinal(otpKey)
        val iv = authorizedCipher.iv
        if (iv.size != KeystoreKeys.IV_SIZE || wrapped.size != OtpKeyFile.WRAPPED_SIZE) {
            throw GeneralSecurityException("Unexpected Keystore output")
        }
        writeFile(file, OtpKeyFile.encode(slot, keyringId, iv, wrapped))
        enrollingSlot = null
        keys.delete(OtpKeyFile.aliasOf(OtpKeyFile.otherSlot(slot)))
    }

    /**
     * Cipher to authorize with BiometricPrompt before [unwrap], or null when this phone has no
     * usable copy of the 2FA key of [keyringId]. A copy the system invalidated is deleted.
     */
    override fun unlockCipher(keyringId: ByteArray): Cipher? {
        val stored = read()?.takeIf { it.keyringId.contentEquals(keyringId) } ?: return null
        val key = keys.get(stored.alias) ?: return null
        return try {
            KeystoreKeys.decryptCipher(key, stored.iv)
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable()
            null
        }
    }

    /** The 2FA key. The caller wipes it. A copy the key does not open is deleted: it is of no use. */
    override fun unwrap(authorizedCipher: Cipher, keyringId: ByteArray): ByteArray {
        val stored = read() ?: throw IOException("The 2FA key of this phone is missing")
        if (!stored.keyringId.contentEquals(keyringId)) throw GeneralSecurityException("2FA key of another vault")
        authorizedCipher.updateAAD(OtpCrypto.deviceAad(keyringId))
        val otpKey = try {
            authorizedCipher.doFinal(stored.wrappedKey)
        } catch (e: AEADBadTagException) {
            disable()
            throw e
        }
        if (otpKey.size != OtpKeyring.KEY_SIZE) {
            otpKey.wipe()
            throw GeneralSecurityException("Invalid 2FA key")
        }
        return otpKey
    }

    override fun disable() {
        enrollingSlot = null
        keys.delete(OtpKeyFile.aliasOf(OtpKeyFile.SLOT_A))
        keys.delete(OtpKeyFile.aliasOf(OtpKeyFile.SLOT_B))
        file.delete()
    }

    private fun read(): OtpKeyFile.Parsed? {
        if (!file.exists()) return null
        val bytes = try {
            readFile(file)
        } catch (e: IOException) {
            return null
        }
        return OtpKeyFile.parse(bytes)
    }
}
