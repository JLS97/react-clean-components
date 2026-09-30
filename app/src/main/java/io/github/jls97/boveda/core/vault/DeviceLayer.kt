package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.AesGcm
import io.github.jls97.boveda.core.crypto.AuthenticationException

/**
 * Outer encryption layer for the copy of the vault stored on the phone. Its key is wrapped by a
 * hardware-backed Android Keystore key that never leaves the device, so a copy of the file taken
 * off the phone can't even be attacked by guessing master passwords: that needs this device.
 *
 * Format: `"BVDE" | version u8 | AES-256-GCM(layerKey, portableVault, aad = "BVDE" | version)`.
 */
object DeviceLayer {
    private const val VERSION: Byte = 1
    private val AAD = byteArrayOf(0x42, 0x56, 0x44, 0x45, VERSION) // "BVDE" + version

    fun seal(layerKey: ByteArray, portableVault: ByteArray): ByteArray =
        AAD + AesGcm.seal(layerKey, portableVault, AAD)

    fun open(layerKey: ByteArray, sealed: ByteArray): ByteArray {
        if (sealed.size < AAD.size + AesGcm.OVERHEAD || !sealed.copyOfRange(0, AAD.size).contentEquals(AAD)) {
            throw CorruptedVaultException("Unknown device layer format")
        }
        return try {
            AesGcm.open(layerKey, sealed.copyOfRange(AAD.size, sealed.size), AAD)
        } catch (e: AuthenticationException) {
            throw DeviceBindingException("The vault was not sealed with this device's key", e)
        }
    }
}
