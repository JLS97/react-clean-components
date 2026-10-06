package io.github.jls97.boveda.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import io.github.jls97.boveda.core.crypto.randomBytes
import io.github.jls97.boveda.core.vault.DeviceBindingException
import io.github.jls97.boveda.data.readFile
import io.github.jls97.boveda.data.writeFile
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException

/**
 * Manages the key of the device layer ([io.github.jls97.boveda.core.vault.DeviceLayer]). The key is
 * stored wrapped by a Keystore key that is bound to this phone and only works while the phone is
 * unlocked (`setUnlockedDeviceRequired`).
 */
internal class DeviceKeyManager(private val keys: KeystoreKeys, private val file: File) {

    /**
     * Returns the layer key, or null if none was created yet.
     *
     * @throws DeviceBindingException if the key exists but this phone's Keystore can never unwrap
     * it again (alias gone, key invalidated, file corrupted).
     * @throws KeystoreUnavailableException if the Keystore did not answer this time (device still
     * locked, busy StrongBox, keystore daemon down): retry later, the key is intact.
     * @throws IOException if the file could not be read.
     */
    fun load(): ByteArray? {
        if (!file.exists()) return null
        val stored = DeviceKeyFile.parse(readFile(file)) ?: throw DeviceBindingException("Invalid wrapped device key")
        return try {
            val key = keys.get(ALIAS) ?: throw DeviceBindingException("Device key is missing from Keystore")
            val cipher = KeystoreKeys.decryptCipher(key, stored.iv)
            cipher.updateAAD(DeviceKeyFile.AAD)
            cipher.doFinal(stored.wrappedKey)
        } catch (e: GeneralSecurityException) {
            throw classify(e)
        } catch (e: ProviderException) {
            throw classify(e)
        }
    }

    /**
     * Returns the existing layer key, or creates a new one only when there is none to keep: no
     * file, or a key the Keystore can never open again. A Keystore that merely did not answer
     * propagates, so a transient failure never rotates the key the current vault is sealed with.
     */
    fun loadOrCreate(): ByteArray =
        try {
            load()
        } catch (e: DeviceBindingException) {
            null
        } ?: create()

    /** Where the Keystore key that wraps the layer key lives. */
    fun securityLevel(): KeySecurityLevel = keys.securityLevel(ALIAS)

    private fun create(): ByteArray {
        // Only reached when there is nothing to keep (no file, or a key that can never open it).
        keys.delete(ALIAS)
        val key = keys.create(ALIAS) { setUnlockedDeviceRequired(true) }
        val layerKey = randomBytes(DeviceKeyFile.KEY_SIZE)
        val cipher = KeystoreKeys.encryptCipher(key)
        cipher.updateAAD(DeviceKeyFile.AAD)
        val wrapped = cipher.doFinal(layerKey)
        writeFile(file, DeviceKeyFile.encode(cipher.iv, wrapped))
        return layerKey
    }

    /**
     * Permanent failures (the key will never open this file again) become [DeviceBindingException];
     * everything else, including `UserNotAuthenticatedException`, `KeyStoreException`,
     * `ProviderException` and `IllegalBlockSizeException`, is the Keystore not answering right now.
     */
    private fun classify(e: Exception): Exception = when (e) {
        is UnrecoverableKeyException, is KeyPermanentlyInvalidatedException, is AEADBadTagException ->
            DeviceBindingException("Keystore can no longer unwrap the device key", e)
        else -> KeystoreUnavailableException("Keystore did not respond", e)
    }

    private companion object {
        const val ALIAS = "boveda.device-layer.v1"
    }
}
