package io.github.jls97.boveda.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import io.github.jls97.boveda.data.readFile
import io.github.jls97.boveda.data.writeFile
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher

/**
 * La copia de la clave de la bóveda protegida por la huella, tal y como la usa la sesión. La
 * implementa [BiometricKeyManager] sobre el Keystore; los tests JVM de la sesión usan un doble.
 */
interface FingerprintKeys {
    fun isEnabled(): Boolean

    fun enrollmentCipher(): Cipher

    fun finishEnrollment(authorizedCipher: Cipher, dek: ByteArray)

    fun unlockCipher(): Cipher?

    fun unwrap(authorizedCipher: Cipher): ByteArray

    fun disable()
}

/**
 * Optional fingerprint unlock. A copy of the vault key (DEK) is encrypted with a Keystore key that
 * requires a strong (class 3) biometric for every single use and is destroyed by the system if a
 * new fingerprint is enrolled, so adding someone else's finger never grants access.
 *
 * The file format is [BiometricKeyFile]. Every enrollment creates its Keystore key under a new
 * alias; the previous one is deleted only after the new copy is written, so a cancelled prompt
 * leaves the current copy intact. Keys left behind are cleaned up on the next enrollment or when
 * the fingerprint is turned off.
 */
internal class BiometricKeyManager(private val keys: KeystoreKeys, private val file: File) : FingerprintKeys {

    /** Alias index of the key [enrollmentCipher] created, until [finishEnrollment] writes its copy. */
    @Volatile
    private var enrollingIndex: Int? = null

    override fun isEnabled(): Boolean {
        val stored = read() ?: return false
        return keys.get(stored.alias) != null
    }

    /** Cipher that must be authorized with BiometricPrompt before [finishEnrollment]. */
    override fun enrollmentCipher(): Cipher {
        enrollingIndex = null
        val current = read()
        val index = BiometricKeyFile.nextIndex(current?.index)
        // Keys of enrollments that never finished are orphans: only the current copy's key stays.
        deleteOrphans(keep = current?.alias)
        val key = keys.create(BiometricKeyFile.aliasOf(index)) {
            setUserAuthenticationRequired(true)
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            setInvalidatedByBiometricEnrollment(true)
        }
        enrollingIndex = index
        return KeystoreKeys.encryptCipher(key)
    }

    /** Writes the copy wrapped by [authorizedCipher] (from [enrollmentCipher]) and drops the previous key. */
    override fun finishEnrollment(authorizedCipher: Cipher, dek: ByteArray) {
        val index = enrollingIndex ?: throw GeneralSecurityException("No fingerprint enrollment in progress")
        val previous = read()
        authorizedCipher.updateAAD(BiometricKeyFile.AAD)
        val wrapped = authorizedCipher.doFinal(dek)
        val iv = authorizedCipher.iv
        if (iv.size != KeystoreKeys.IV_SIZE || wrapped.size != BiometricKeyFile.WRAPPED_SIZE) {
            throw GeneralSecurityException("Unexpected Keystore output")
        }
        writeFile(file, BiometricKeyFile.encode(index, iv, wrapped))
        enrollingIndex = null
        if (previous != null && previous.index != index) keys.delete(previous.alias)
    }

    /**
     * Cipher that must be authorized with BiometricPrompt before [unwrap], or null when biometric
     * unlock is off or the system invalidated the key (for example, after a new fingerprint).
     */
    override fun unlockCipher(): Cipher? {
        val stored = read() ?: return null
        val key = keys.get(stored.alias)
        if (key == null) {
            disable()
            return null
        }
        return try {
            KeystoreKeys.decryptCipher(key, stored.iv)
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable()
            null
        }
    }

    /** The DEK. The caller wipes it. A copy the key does not open is deleted: it is of no use. */
    override fun unwrap(authorizedCipher: Cipher): ByteArray {
        val stored = read() ?: throw IOException("The fingerprint key of this phone is missing")
        stored.aad?.let { authorizedCipher.updateAAD(it) }
        return try {
            authorizedCipher.doFinal(stored.wrappedKey)
        } catch (e: AEADBadTagException) {
            disable()
            throw e
        }
    }

    override fun disable() {
        enrollingIndex = null
        file.delete()
        deleteOrphans(keep = null)
    }

    private fun read(): BiometricKeyFile.Parsed? {
        if (!file.exists()) return null
        val bytes = try {
            readFile(file)
        } catch (e: IOException) {
            return null
        }
        return BiometricKeyFile.parse(bytes)
    }

    /** Deletes every fingerprint key of this app except [keep]. */
    private fun deleteOrphans(keep: String?) {
        for (alias in keys.aliases(BiometricKeyFile.ALIAS_PREFIX)) {
            if (alias != keep) keys.delete(alias)
        }
    }
}
