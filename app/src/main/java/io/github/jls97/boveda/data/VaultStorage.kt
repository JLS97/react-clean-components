package io.github.jls97.boveda.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException

/**
 * Files of the vault. They live in no-backup storage, which neither cloud backups nor device
 * transfers copy, and the manifest excludes everything from backups as well. Every file name is
 * defined here and nowhere else.
 *
 * What the file system still shows to root or to a forensic image, even though every content is
 * encrypted (vault.bin under two layers, the key files under the Keystore): the size of
 * `vault.bin`, which grows with the number and length of the entries because the payload is not
 * padded; its modification time, which is the last time the vault changed; and the mere existence
 * of `biometric.key` (fingerprint unlock on), `otp.key` (2FA codes stored; its keyring id is in
 * clear so a restored backup never uses a stale copy) and `vault.prev.bin` (a restore that can
 * still be undone). None of it is secret and no process without root can read the directory; the
 * only way to hide it would be to pad and rewrite every file on every change, so the trade-off is
 * documented instead.
 */
internal class VaultStorage(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)

    val vaultFile = File(directory, VAULT_FILE_NAME)

    /** The vault.bin the last restore replaced, sealed with the same layer key, until the next restore or an undo. */
    val previousVaultFile = File(directory, PREVIOUS_VAULT_FILE_NAME)
    val layerKeyFile = File(directory, LAYER_KEY_FILE_NAME)
    val biometricKeyFile = File(directory, BIOMETRIC_KEY_FILE_NAME)
    val otpKeyFile = File(directory, OTP_KEY_FILE_NAME)

    fun vaultExists(): Boolean = vaultFile.exists()

    fun readVault(): ByteArray = readFile(vaultFile)

    fun writeVault(bytes: ByteArray) = writeFile(vaultFile, bytes)

    fun previousVaultExists(): Boolean = previousVaultFile.exists()

    fun readPreviousVault(): ByteArray = readFile(previousVaultFile)

    fun writePreviousVault(bytes: ByteArray) = writeFile(previousVaultFile, bytes)

    fun deletePreviousVault() = deleteArtifact(previousVaultFile)

    /** Every file the app may keep in its directory, present or not. */
    fun artifacts(): List<File> = listOf(vaultFile, previousVaultFile, layerKeyFile, biometricKeyFile, otpKeyFile)

    /** The artifacts that exist right now. */
    fun existingArtifacts(): List<File> = artifacts().filter { it.exists() }

    /** Deletes an artifact together with the temporary files a write may have left next to it. */
    fun deleteArtifact(file: File) = AtomicFile(file).delete()

    companion object {
        const val DIRECTORY_NAME = "vault"
        const val VAULT_FILE_NAME = "vault.bin"
        const val PREVIOUS_VAULT_FILE_NAME = "vault.prev.bin"
        const val LAYER_KEY_FILE_NAME = "layer.key"
        const val BIOMETRIC_KEY_FILE_NAME = "biometric.key"
        const val OTP_KEY_FILE_NAME = "otp.key"
    }
}

internal fun readFile(file: File): ByteArray = AtomicFile(file).readFully()

/** Writes to a temporary file, syncs it to disk and renames it, so a crash never leaves half a file. */
internal fun writeFile(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val atomicFile = AtomicFile(file)
    val stream = atomicFile.startWrite()
    try {
        stream.write(bytes)
        atomicFile.finishWrite(stream)
    } catch (e: IOException) {
        atomicFile.failWrite(stream)
        throw e
    }
}
