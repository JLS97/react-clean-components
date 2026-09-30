package io.github.jls97.boveda.core.otp

import io.github.jls97.boveda.core.crypto.AesGcm
import io.github.jls97.boveda.core.crypto.Argon2Kdf
import io.github.jls97.boveda.core.crypto.AuthenticationException
import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.crypto.randomBytes
import io.github.jls97.boveda.core.crypto.secureRandom
import io.github.jls97.boveda.core.crypto.wipe
import io.github.jls97.boveda.core.vault.ByteReader
import io.github.jls97.boveda.core.vault.ByteWriter
import io.github.jls97.boveda.core.vault.CorruptedVaultException
import io.github.jls97.boveda.core.vault.OtpKeyring
import io.github.jls97.boveda.core.vault.SealedOtp
import io.github.jls97.boveda.core.vault.UnsupportedVaultException
import io.github.jls97.boveda.core.vault.VaultException
import io.github.jls97.boveda.core.vault.asInt
import io.github.jls97.boveda.core.vault.asString
import io.github.jls97.boveda.core.vault.putBytesField
import io.github.jls97.boveda.core.vault.putIntField
import io.github.jls97.boveda.core.vault.putStringField
import io.github.jls97.boveda.core.vault.readFields

class WrongRecoveryCodeException : VaultException("Wrong 2FA recovery code")

/**
 * Encryption of the 2FA secrets. Each secret is sealed with the 2FA key, a random AES-256 key
 * that is never stored in the clear:
 *
 * - on the phone, it is wrapped by a Keystore key that needs a fingerprint for every use;
 * - inside the vault (and so in backups), it is wrapped by a key derived with Argon2id from the
 *   recovery code, which the user keeps on paper.
 *
 * So unlocking the vault, with the master password or a backup, still doesn't reveal any code.
 */
object OtpCrypto {
    private const val SALT_SIZE = 32
    private const val RECORD_VERSION = 1

    private const val FIELD_KEY = 1
    private const val FIELD_ALGORITHM = 2
    private const val FIELD_DIGITS = 3
    private const val FIELD_PERIOD = 4
    private const val FIELD_ISSUER = 5
    private const val FIELD_ACCOUNT = 6

    private val SECRET_AAD = "boveda/otp-secret/v1".toByteArray()
    private val RECOVERY_AAD = "boveda/otp-recovery/v1".toByteArray()
    private val DEVICE_AAD = "boveda/otp-device/v1".toByteArray()

    fun newKey(): ByteArray = randomBytes(OtpKeyring.KEY_SIZE)

    fun newKeyringId(): ByteArray = randomBytes(OtpKeyring.ID_SIZE)

    /** Associated data for the phone's Keystore-wrapped copy of the 2FA key. */
    fun deviceAad(keyringId: ByteArray): ByteArray = DEVICE_AAD + keyringId

    /** Wraps [otpKey] under [recoveryCode] (canonical form, see [RecoveryCode]). Slow: Argon2id. */
    fun createKeyring(
        otpKey: ByteArray,
        keyringId: ByteArray,
        recoveryCode: CharArray,
        params: KdfParams = KdfParams.DEFAULT,
    ): OtpKeyring {
        require(otpKey.size == OtpKeyring.KEY_SIZE && keyringId.size == OtpKeyring.ID_SIZE)
        val salt = randomBytes(SALT_SIZE)
        val kek = Argon2Kdf.deriveKey(recoveryCode, salt, params)
        try {
            val wrapped = AesGcm.seal(kek, otpKey, recoveryAad(keyringId, params, salt))
            return OtpKeyring(keyringId, params, salt, wrapped)
        } finally {
            kek.wipe()
        }
    }

    /**
     * The 2FA key, recovered with the code written on paper. Slow: Argon2id.
     *
     * @throws WrongRecoveryCodeException if the code is not the one of this keyring.
     */
    fun unwrapWithRecoveryCode(keyring: OtpKeyring, recoveryCode: CharArray): ByteArray {
        val salt = keyring.salt
        val kek = Argon2Kdf.deriveKey(recoveryCode, salt, keyring.kdfParams)
        try {
            return AesGcm.open(kek, keyring.wrappedKey, recoveryAad(keyring.id, keyring.kdfParams, salt))
        } catch (e: AuthenticationException) {
            throw WrongRecoveryCodeException()
        } finally {
            kek.wipe()
        }
    }

    /** Encrypts [secret] for the entry [entryId]; it can't be moved to another entry or keyring. */
    fun seal(otpKey: ByteArray, keyringId: ByteArray, entryId: String, secret: OtpSecret): SealedOtp {
        val writer = ByteWriter(256)
        val key = secret.key
        try {
            writer.putU16(RECORD_VERSION)
            writer.putU16(6)
            writer.putBytesField(FIELD_KEY, key)
            writer.putIntField(FIELD_ALGORITHM, secret.params.algorithm.ordinal + 1)
            writer.putIntField(FIELD_DIGITS, secret.params.digits)
            writer.putIntField(FIELD_PERIOD, secret.params.period)
            writer.putStringField(FIELD_ISSUER, secret.issuer)
            writer.putStringField(FIELD_ACCOUNT, secret.account)
            val plaintext = writer.toByteArray()
            try {
                return SealedOtp(AesGcm.seal(otpKey, plaintext, secretAad(keyringId, entryId)))
            } finally {
                plaintext.wipe()
            }
        } finally {
            key.wipe()
            writer.wipe()
        }
    }

    /**
     * Decrypts the secret of the entry [entryId]. The caller wipes the result.
     *
     * @throws CorruptedVaultException if it was altered, or belongs to another entry or keyring.
     */
    fun open(otpKey: ByteArray, keyringId: ByteArray, entryId: String, sealed: SealedOtp): OtpSecret {
        val plaintext = try {
            AesGcm.open(otpKey, sealed.bytes, secretAad(keyringId, entryId))
        } catch (e: AuthenticationException) {
            throw CorruptedVaultException("2FA secret failed authentication", e)
        }
        var key: ByteArray? = null
        try {
            val reader = ByteReader(plaintext)
            val version = reader.readU16()
            if (version != RECORD_VERSION) throw UnsupportedVaultException("Unsupported 2FA record $version")
            var algorithm: OtpAlgorithm? = null
            var digits = 0
            var period = 0
            var issuer = ""
            var account = ""
            reader.readFields { tag, value ->
                when (tag) {
                    FIELD_KEY -> {
                        key?.wipe()
                        key = value.copyOf()
                    }
                    FIELD_ALGORITHM -> algorithm = OtpAlgorithm.entries.getOrNull(value.asInt() - 1)
                        ?: throw UnsupportedVaultException("Unsupported 2FA algorithm")
                    FIELD_DIGITS -> digits = value.asInt()
                    FIELD_PERIOD -> period = value.asInt()
                    FIELD_ISSUER -> issuer = value.asString()
                    FIELD_ACCOUNT -> account = value.asString()
                }
            }
            val validKey = key?.takeIf { it.size in 1..OtpInput.MAX_SECRET_BYTES }
            val validAlgorithm = algorithm
            if (reader.remaining != 0 || validKey == null || validAlgorithm == null ||
                digits !in OtpParams.DIGITS || period !in OtpParams.PERIOD_SECONDS
            ) {
                throw CorruptedVaultException("Invalid 2FA record")
            }
            return OtpSecret(validKey, OtpParams(validAlgorithm, digits, period), issuer, account)
        } finally {
            key?.wipe()
            plaintext.wipe()
        }
    }

    private fun secretAad(keyringId: ByteArray, entryId: String): ByteArray =
        SECRET_AAD + keyringId + entryId.toByteArray(Charsets.UTF_8)

    private fun recoveryAad(keyringId: ByteArray, params: KdfParams, salt: ByteArray): ByteArray {
        val writer = ByteWriter(96)
        writer.putBytes(RECOVERY_AAD)
        writer.putBytes(keyringId)
        writer.putI32(params.memoryKiB)
        writer.putI32(params.iterations)
        writer.putU8(params.parallelism)
        writer.putBytes(salt)
        return writer.toByteArray()
    }
}

/**
 * The code that recovers the 2FA key on another phone or after a fingerprint change: 20 symbols
 * (100 random bits) of Crockford's base32, which has no easily confused letters, written as
 * `XXXXX-XXXXX-XXXXX-XXXXX`.
 */
object RecoveryCode {
    const val LENGTH = 20
    private const val GROUP = 5
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** A new random code in canonical form: 20 symbols, no separators. */
    fun generate(): CharArray = CharArray(LENGTH) { ALPHABET[secureRandom.nextInt(ALPHABET.length)] }

    fun format(code: CharArray): String = code.toList().chunked(GROUP).joinToString("-") { it.joinToString("") }

    /**
     * The canonical form of what the user typed, or null if it can't be a code. Case, spaces and
     * hyphens don't matter, and O, I and L are read as 0, 1 and 1.
     */
    fun normalize(input: CharSequence): CharArray? {
        val symbols = CharArray(LENGTH)
        var count = 0
        for (char in input) {
            if (char == ' ' || char == '-') continue
            val symbol = when (val upper = char.uppercaseChar()) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> upper
            }
            if (symbol !in ALPHABET || count == LENGTH) {
                symbols.wipe()
                return null
            }
            symbols[count++] = symbol
        }
        if (count != LENGTH) {
            symbols.wipe()
            return null
        }
        return symbols
    }
}
