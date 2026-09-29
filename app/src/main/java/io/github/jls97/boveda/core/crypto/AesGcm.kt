package io.github.jls97.boveda.core.crypto

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a fresh random 96-bit nonce per message. The sealed form is
 * `nonce || ciphertext || tag`, and the associated data (AAD) is authenticated but not stored.
 */
object AesGcm {
    const val KEY_SIZE = 32
    const val NONCE_SIZE = 12
    const val TAG_SIZE = 16

    /** Smallest possible sealed message: nonce and tag around an empty plaintext. */
    const val OVERHEAD = NONCE_SIZE + TAG_SIZE

    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "AES-256 requires a 32-byte key" }
        val nonce = randomBytes(NONCE_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE * 8, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    /** @throws AuthenticationException if the key, the AAD or the sealed data do not match. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "AES-256 requires a 32-byte key" }
        if (sealed.size < OVERHEAD) throw AuthenticationException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_SIZE * 8, sealed, 0, NONCE_SIZE),
        )
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(sealed, NONCE_SIZE, sealed.size - NONCE_SIZE)
        } catch (e: AEADBadTagException) {
            throw AuthenticationException()
        }
    }
}

/** The authentication tag did not verify: wrong key, wrong associated data or tampered data. */
class AuthenticationException : Exception("Authentication tag mismatch")
