package io.github.jls97.boveda.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException

/**
 * Files of the vault. They live in no-backup storage, which neither cloud backups nor device
 * transfers copy, and the manifest excludes everything from backups as well.
 */
internal class VaultStorage(context: Context) {
    private val directory = File(context.noBackupFilesDir, "vault")

    val vaultFile = File(directory, "vault.bin")
    val layerKeyFile = File(directory, "layer.key")
    val biometricKeyFile = File(directory, "biometric.key")
    val otpKeyFile = File(directory, "otp.key")

    fun vaultExists(): Boolean = vaultFile.exists()

    fun readVault(): ByteArray = readFile(vaultFile)

    fun writeVault(bytes: ByteArray) = writeFile(vaultFile, bytes)
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
