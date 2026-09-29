package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.AesGcm
import io.github.jls97.boveda.core.crypto.Argon2Kdf
import io.github.jls97.boveda.core.crypto.AuthenticationException
import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.crypto.randomBytes
import io.github.jls97.boveda.core.crypto.wipe

/**
 * Portable vault file protected only by the master password. It is also the backup format, so a
 * backup opens on any device that has the password.
 *
 * ```
 * header:  "BOVD" | version u8 | kdf u8 | memoryKiB i32 | iterations i32 | parallelism u8
 *          | saltLength u8 | salt | wrappedDekLength u8 | wrappedDek
 * body:    AES-256-GCM(DEK, payload, aad = header)
 * ```
 *
 * A random data-encryption key (DEK) encrypts the payload. The DEK is wrapped with a key derived
 * from the master password with Argon2id, authenticating the KDF section of the header as AAD.
 * Changing the password only re-wraps the DEK.
 */
object VaultContainer {
    private val MAGIC = byteArrayOf(0x42, 0x4F, 0x56, 0x44) // "BOVD"
    private const val FORMAT_VERSION = 1
    private const val KDF_ARGON2ID = 1
    private const val SALT_SIZE = 32
    private const val DEK_SIZE = 32
    private const val WRAPPED_DEK_SIZE = DEK_SIZE + AesGcm.OVERHEAD

    class Header internal constructor(
        val kdfParams: KdfParams,
        internal val encoded: ByteArray,
        private val kdfSectionLength: Int,
        private val saltOffset: Int,
        private val saltLength: Int,
    ) {
        internal val kdfSection: ByteArray get() = encoded.copyOfRange(0, kdfSectionLength)
        internal val salt: ByteArray get() = encoded.copyOfRange(saltOffset, saltOffset + saltLength)
        internal val wrappedDek: ByteArray get() = encoded.copyOfRange(kdfSectionLength + 1, encoded.size)
    }

    /** An opened vault. The caller owns [dek] and must wipe it when locking. */
    class Opened(val header: Header, val dek: ByteArray, val data: VaultData)

    /** Creates a new vault with a random DEK. Slow on purpose: runs Argon2id. */
    fun create(password: CharArray, data: VaultData, params: KdfParams = KdfParams.DEFAULT): Opened {
        val dek = randomBytes(DEK_SIZE)
        return Opened(buildHeader(password, dek, params), dek, data)
    }

    /** Encrypts [data] with a fresh nonce under the existing header. Fast: no key derivation. */
    fun seal(header: Header, dek: ByteArray, data: VaultData): ByteArray {
        val payload = VaultCodec.encode(data)
        try {
            return header.encoded + AesGcm.seal(dek, payload, header.encoded)
        } finally {
            payload.wipe()
        }
    }

    /**
     * Opens a vault with the master password.
     *
     * @throws WrongPasswordException if the password is wrong or the header was altered.
     * @throws CorruptedVaultException if the file is damaged.
     * @throws UnsupportedVaultException if the file uses an unknown format or absurd KDF costs.
     */
    fun open(blob: ByteArray, password: CharArray): Opened {
        val header = parseHeader(blob)
        val kek = Argon2Kdf.deriveKey(password, header.salt, header.kdfParams)
        val dek = try {
            AesGcm.open(kek, header.wrappedDek, header.kdfSection)
        } catch (e: AuthenticationException) {
            throw WrongPasswordException()
        } finally {
            kek.wipe()
        }
        return openBody(blob, header, dek)
    }

    /** Opens a vault with an already known DEK (biometric unlock). */
    fun openWithKey(blob: ByteArray, dek: ByteArray): Opened = openBody(blob, parseHeader(blob), dek.copyOf())

    /** Re-wraps the same DEK under a new password and a new salt. Slow: runs Argon2id. */
    fun changePassword(dek: ByteArray, newPassword: CharArray, params: KdfParams = KdfParams.DEFAULT): Header =
        buildHeader(newPassword, dek, params)

    /** True if [password] unwraps this header's DEK. Slow: runs Argon2id. */
    fun verifyPassword(header: Header, password: CharArray): Boolean {
        val kek = Argon2Kdf.deriveKey(password, header.salt, header.kdfParams)
        return try {
            AesGcm.open(kek, header.wrappedDek, header.kdfSection).wipe()
            true
        } catch (e: AuthenticationException) {
            false
        } finally {
            kek.wipe()
        }
    }

    private fun openBody(blob: ByteArray, header: Header, dek: ByteArray): Opened {
        val body = blob.copyOfRange(header.encoded.size, blob.size)
        val payload = try {
            AesGcm.open(dek, body, header.encoded)
        } catch (e: AuthenticationException) {
            dek.wipe()
            throw CorruptedVaultException("Vault contents failed authentication", e)
        }
        try {
            return Opened(header, dek, VaultCodec.decode(payload))
        } catch (e: VaultException) {
            dek.wipe()
            throw e
        } finally {
            payload.wipe()
        }
    }

    private fun buildHeader(password: CharArray, dek: ByteArray, params: KdfParams): Header {
        val salt = randomBytes(SALT_SIZE)
        val kdfWriter = ByteWriter(64).apply {
            putBytes(MAGIC)
            putU8(FORMAT_VERSION)
            putU8(KDF_ARGON2ID)
            putI32(params.memoryKiB)
            putI32(params.iterations)
            putU8(params.parallelism)
            putU8(salt.size)
            putBytes(salt)
        }
        val kdfSection = kdfWriter.toByteArray()
        val kek = Argon2Kdf.deriveKey(password, salt, params)
        val wrappedDek = try {
            AesGcm.seal(kek, dek, kdfSection)
        } finally {
            kek.wipe()
        }
        val encoded = kdfSection + byteArrayOf(wrappedDek.size.toByte()) + wrappedDek
        return Header(
            kdfParams = params,
            encoded = encoded,
            kdfSectionLength = kdfSection.size,
            saltOffset = kdfSection.size - salt.size,
            saltLength = salt.size,
        )
    }

    /** Parses and validates the header. It does not prove the password or the data are right. */
    fun parseHeader(blob: ByteArray): Header {
        val reader = ByteReader(blob)
        if (!reader.readBytes(MAGIC.size).contentEquals(MAGIC)) {
            throw CorruptedVaultException("Not a Bóveda vault file")
        }
        val version = reader.readU8()
        if (version != FORMAT_VERSION) throw UnsupportedVaultException("Unsupported vault format $version")
        val kdf = reader.readU8()
        if (kdf != KDF_ARGON2ID) throw UnsupportedVaultException("Unsupported key derivation $kdf")

        val memoryKiB = reader.readI32()
        val iterations = reader.readI32()
        val parallelism = reader.readU8()
        if (!KdfParams.isValid(memoryKiB, iterations, parallelism)) {
            throw UnsupportedVaultException("Key derivation parameters out of range")
        }

        val saltLength = reader.readU8()
        if (saltLength !in 16..64) throw CorruptedVaultException("Invalid salt length")
        val saltOffset = reader.offset
        reader.readBytes(saltLength)
        val kdfSectionLength = reader.offset

        val wrappedLength = reader.readU8()
        if (wrappedLength != WRAPPED_DEK_SIZE) throw CorruptedVaultException("Invalid wrapped key")
        reader.readBytes(wrappedLength)
        val headerLength = reader.offset

        if (reader.remaining < AesGcm.OVERHEAD) throw CorruptedVaultException("Missing vault contents")
        return Header(
            kdfParams = KdfParams(memoryKiB, iterations, parallelism),
            encoded = blob.copyOfRange(0, headerLength),
            kdfSectionLength = kdfSectionLength,
            saltOffset = saltOffset,
            saltLength = saltLength,
        )
    }
}
