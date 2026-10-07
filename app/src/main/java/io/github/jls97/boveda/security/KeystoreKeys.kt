package io.github.jls97.boveda.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * The phone's Keystore did not answer (locked device, busy StrongBox, keystore daemon restarting):
 * the key is still there and the same call usually works a moment later or after a reboot. Never
 * a reason to replace a key or to restore a backup.
 */
class KeystoreUnavailableException(message: String, cause: Throwable? = null) : GeneralSecurityException(message, cause)

/** Where the material of a Keystore key lives, as the system reports it ([KeyInfo.getSecurityLevel]). */
enum class KeySecurityLevel(val label: String) {
    /** A dedicated security chip. */
    STRONGBOX("StrongBox"),

    /** The trusted execution environment of the main processor. */
    TEE("TEE"),

    /** Software only: a copy of the app's files taken off the phone could be opened elsewhere. */
    SOFTWARE("Software"),

    /** The system did not say, or the key could not be inspected. */
    UNKNOWN("desconocido"),
}

/**
 * AES-256-GCM keys that live in Android Keystore. Their key material never enters the app's
 * memory: encryption runs inside the secure hardware (StrongBox if present, otherwise the TEE).
 */
internal class KeystoreKeys(private val context: Context) {
    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    /**
     * The key under [alias], or null when there is none. Android answers null only when the
     * keystore says the key does not exist and throws `UnrecoverableKeyException` for every other
     * failure, transient ones included, so that exception is read by its error code
     * ([KeystoreFailurePolicy]): a key that is gone or corrupted is null as well, and anything else
     * is a [KeystoreUnavailableException] to retry later.
     */
    fun get(alias: String): SecretKey? =
        try {
            keyStore.getKey(alias, null) as? SecretKey
        } catch (e: UnrecoverableKeyException) {
            when (KeystoreFailurePolicy.classifyUnrecoverableKey(KeystoreFailurePolicy.keystoreErrorCode(e))) {
                KeystoreFailurePolicy.Kind.PERMANENT -> null
                KeystoreFailurePolicy.Kind.TRANSIENT -> throw KeystoreUnavailableException("Keystore did not respond", e)
            }
        }

    fun delete(alias: String) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    /** Aliases that start with [prefix]; empty if the Keystore could not list them. */
    fun aliases(prefix: String): List<String> =
        try {
            keyStore.aliases().toList().filter { it.startsWith(prefix) }
        } catch (e: KeyStoreException) {
            emptyList()
        }

    /**
     * Creates a key, preferring StrongBox (a dedicated security chip) when the phone has one. A key
     * already stored under [alias] is replaced; callers that still need the old key enroll under a
     * new alias and delete the old one themselves once the new copy is written.
     */
    fun create(alias: String, configure: KeyGenParameterSpec.Builder.() -> Unit): SecretKey {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            try {
                return generate(alias, strongBox = true, configure)
            } catch (e: ProviderException) {
                // StrongBoxUnavailableException (a ProviderException) or a Keystore failure of the
                // chip: fall back to the TEE, which is still hardware-isolated.
            } catch (e: InvalidAlgorithmParameterException) {
                // A parameter combination this chip rejects.
            }
        }
        return generate(alias, strongBox = false, configure)
    }

    /** Where the key [alias] lives, or [KeySecurityLevel.UNKNOWN] if it is missing or can't be inspected. */
    fun securityLevel(alias: String): KeySecurityLevel {
        val key = try {
            get(alias)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: ProviderException) {
            null
        } ?: return KeySecurityLevel.UNKNOWN
        return try {
            val factory = SecretKeyFactory.getInstance(key.algorithm, PROVIDER)
            when ((factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo).securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TEE
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.SOFTWARE
                else -> KeySecurityLevel.UNKNOWN
            }
        } catch (e: GeneralSecurityException) {
            KeySecurityLevel.UNKNOWN
        } catch (e: ProviderException) {
            KeySecurityLevel.UNKNOWN
        } catch (e: ClassCastException) {
            KeySecurityLevel.UNKNOWN
        }
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
        const val IV_SIZE = KEYSTORE_IV_SIZE

        /** Keystore picks the IV itself; read it from [Cipher.getIV] after encrypting. */
        fun encryptCipher(key: SecretKey): Cipher =
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

        fun decryptCipher(key: SecretKey, iv: ByteArray): Cipher =
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
    }
}
