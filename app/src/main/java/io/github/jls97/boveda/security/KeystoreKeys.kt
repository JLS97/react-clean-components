package io.github.jls97.boveda.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM keys that live in Android Keystore. Their key material never enters the app's
 * memory: encryption runs inside the secure hardware (StrongBox if present, otherwise the TEE).
 */
internal class KeystoreKeys(private val context: Context) {
    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    fun get(alias: String): SecretKey? = keyStore.getKey(alias, null) as? SecretKey

    fun delete(alias: String) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    /** Creates a key, preferring StrongBox (a dedicated security chip) when the phone has one. */
    fun create(alias: String, configure: KeyGenParameterSpec.Builder.() -> Unit): SecretKey {
        delete(alias)
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            try {
                return generate(alias, strongBox = true, configure)
            } catch (e: Exception) {
                // StrongBoxUnavailableException, or a parameter combination this chip rejects:
                // fall back to the TEE, which is still hardware-isolated.
            }
        }
        return generate(alias, strongBox = false, configure)
    }

    private fun generate(
        alias: String,
        strongBox: Boolean,
        configure: KeyGenParameterSpec.Builder.() -> Unit,
    ): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setIsStrongBoxBacked(strongBox)
        builder.configure()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(builder.build())
        return generator.generateKey()
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12

        /** Keystore picks the IV itself; read it from [Cipher.getIV] after encrypting. */
        fun encryptCipher(key: SecretKey): Cipher =
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

        fun decryptCipher(key: SecretKey, iv: ByteArray): Cipher =
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
    }
}
