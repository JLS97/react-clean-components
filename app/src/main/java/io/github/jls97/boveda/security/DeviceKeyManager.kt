package io.github.jls97.boveda.security

import io.github.jls97.boveda.core.crypto.randomBytes
import io.github.jls97.boveda.core.vault.DeviceBindingException
import io.github.jls97.boveda.data.readFile
import io.github.jls97.boveda.data.writeFile
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * Manages the key of the device layer ([io.github.jls97.boveda.core.vault.DeviceLayer]). The key is
 * stored wrapped by a Keystore key that is bound to this phone and only works while the phone is
 * unlocked (`setUnlockedDeviceRequired`).
 */
internal class DeviceKeyManager(private val keys: KeystoreKeys, private val file: File) {

    /**
     * Returns the layer key, or null if none was created yet.
     *
     * @throws DeviceBindingException if the key exists but this phone's Keystore can't unwrap it.
     */
    fun load(): ByteArray? {
        if (!file.exists()) return null
        val key = keys.get(ALIAS) ?: throw DeviceBindingException("Device key is missing from Keystore")
        return try {
            val stored = readFile(file)
            if (stored.size <= KeystoreKeys.IV_SIZE) throw DeviceBindingException("Invalid wrapped device key")
            val cipher = KeystoreKeys.decryptCipher(key, stored.copyOfRange(0, KeystoreKeys.IV_SIZE))
            cipher.updateAAD(AAD)
            cipher.doFinal(stored, KeystoreKeys.IV_SIZE, stored.size - KeystoreKeys.IV_SIZE)
        } catch (e: GeneralSecurityException) {
            throw DeviceBindingException("Keystore could not unwrap the device key", e)
        } catch (e: ProviderException) {
            throw DeviceBindingException("Keystore could not unwrap the device key", e)
        } catch (e: IOException) {
            throw DeviceBindingException("Could not read the device key", e)
        }
    }

    /** Returns the existing layer key, or creates a new one if there is none or it is unusable. */
    fun loadOrCreate(): ByteArray =
        try {
            load()
        } catch (e: DeviceBindingException) {
            null
        } ?: create()

    private fun create(): ByteArray {
        val key = keys.create(ALIAS) { setUnlockedDeviceRequired(true) }
        val layerKey = randomBytes(32)
        val cipher = KeystoreKeys.encryptCipher(key)
        cipher.updateAAD(AAD)
        val wrapped = cipher.doFinal(layerKey)
        writeFile(file, cipher.iv + wrapped)
        return layerKey
    }

    private companion object {
        const val ALIAS = "boveda.device-layer.v1"
        val AAD = "boveda/device-layer-key/v1".toByteArray()
    }
}
