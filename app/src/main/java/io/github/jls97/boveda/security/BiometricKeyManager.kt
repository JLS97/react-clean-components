package io.github.jls97.boveda.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import io.github.jls97.boveda.data.readFile
import io.github.jls97.boveda.data.writeFile
import java.io.File
import javax.crypto.Cipher

/**
 * Optional fingerprint unlock. A copy of the vault key (DEK) is encrypted with a Keystore key that
 * requires a strong (class 3) biometric for every single use and is destroyed by the system if a
 * new fingerprint is enrolled, so adding someone else's finger never grants access.
 */
internal class BiometricKeyManager(private val keys: KeystoreKeys, private val file: File) {

    fun isEnabled(): Boolean = file.exists() && keys.get(ALIAS) != null

    /** Cipher that must be authorized with BiometricPrompt before [finishEnrollment]. */
    fun enrollmentCipher(): Cipher {
        val key = keys.create(ALIAS) {
            setUserAuthenticationRequired(true)
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            setInvalidatedByBiometricEnrollment(true)
        }
        return KeystoreKeys.encryptCipher(key)
    }

    fun finishEnrollment(authorizedCipher: Cipher, dek: ByteArray) {
        val wrapped = authorizedCipher.doFinal(dek)
        writeFile(file, authorizedCipher.iv + wrapped)
    }

    /**
     * Cipher that must be authorized with BiometricPrompt before [unwrap], or null when biometric
     * unlock is off or the system invalidated the key (for example, after a new fingerprint).
     */
    fun unlockCipher(): Cipher? {
        if (!file.exists()) return null
        val key = keys.get(ALIAS)
        if (key == null) {
            disable()
            return null
        }
        val stored = readFile(file)
        return try {
            KeystoreKeys.decryptCipher(key, stored.copyOfRange(0, KeystoreKeys.IV_SIZE))
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable()
            null
        }
    }

    fun unwrap(authorizedCipher: Cipher): ByteArray {
        val stored = readFile(file)
        return authorizedCipher.doFinal(stored, KeystoreKeys.IV_SIZE, stored.size - KeystoreKeys.IV_SIZE)
    }

    fun disable() {
        keys.delete(ALIAS)
        file.delete()
    }

    private companion object {
        const val ALIAS = "boveda.biometric.v1"
    }
}
