package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.AesGcm
import io.github.jls97.boveda.core.crypto.KdfParams

/**
 * Serializes [VaultData] to the plaintext that gets encrypted. Every record is a list of
 * tagged fields (`tag u16, length u32, value`), so newer versions can add fields and older
 * readers skip the tags they don't know. The first record holds what applies to the whole vault:
 * the settings and, once 2FA is in use, its keyring.
 */
object VaultCodec {
    private const val PAYLOAD_VERSION = 1
    private const val MAX_ENTRIES = 100_000

    private const val SETTING_AUTO_LOCK = 1
    private const val SETTING_CLIPBOARD_CLEAR = 2
    private const val VAULT_OTP_KEYRING = 3

    private const val ENTRY_ID = 1
    private const val ENTRY_TITLE = 2
    private const val ENTRY_USERNAME = 3
    private const val ENTRY_PASSWORD = 4
    private const val ENTRY_URL = 5
    private const val ENTRY_NOTES = 6
    private const val ENTRY_CREATED_AT = 7
    private const val ENTRY_UPDATED_AT = 8
    private const val ENTRY_AUTOFILL_TARGETS = 9
    private const val ENTRY_OTP = 10

    private const val KEYRING_ID = 1
    private const val KEYRING_MEMORY_KIB = 2
    private const val KEYRING_ITERATIONS = 3
    private const val KEYRING_PARALLELISM = 4
    private const val KEYRING_SALT = 5
    private const val KEYRING_WRAPPED_KEY = 6

    private const val MAX_SEALED_OTP_SIZE = 4_096

    fun encode(data: VaultData): ByteArray {
        val writer = ByteWriter(4_096)
        try {
            writer.putU16(PAYLOAD_VERSION)

            val keyring = data.otpKeyring
            writer.putU16(if (keyring == null) 2 else 3)
            writer.putIntField(SETTING_AUTO_LOCK, data.settings.autoLockSeconds)
            writer.putIntField(SETTING_CLIPBOARD_CLEAR, data.settings.clipboardClearSeconds)
            if (keyring != null) writer.putBytesField(VAULT_OTP_KEYRING, encodeKeyring(keyring))

            writer.putI32(data.entries.size)
            for (entry in data.entries) {
                val otp = entry.otp
                writer.putU16(if (otp == null) 9 else 10)
                writer.putStringField(ENTRY_ID, entry.id)
                writer.putStringField(ENTRY_TITLE, entry.title)
                writer.putStringField(ENTRY_USERNAME, entry.username)
                writer.putStringField(ENTRY_PASSWORD, entry.password)
                writer.putStringField(ENTRY_URL, entry.url)
                writer.putStringField(ENTRY_NOTES, entry.notes)
                writer.putLongField(ENTRY_CREATED_AT, entry.createdAt)
                writer.putLongField(ENTRY_UPDATED_AT, entry.updatedAt)
                // Package names and domains never contain line breaks.
                writer.putStringField(ENTRY_AUTOFILL_TARGETS, entry.autofillTargets.joinToString("\n"))
                if (otp != null) writer.putBytesField(ENTRY_OTP, otp.bytes)
            }
            return writer.toByteArray()
        } finally {
            writer.wipe()
        }
    }

    fun decode(payload: ByteArray): VaultData {
        val reader = ByteReader(payload)
        val version = reader.readU16()
        if (version != PAYLOAD_VERSION) throw UnsupportedVaultException("Unsupported payload version $version")

        var settings = VaultSettings()
        var keyring: OtpKeyring? = null
        reader.readFields { tag, value ->
            when (tag) {
                SETTING_AUTO_LOCK -> settings = settings.copy(autoLockSeconds = value.asInt())
                SETTING_CLIPBOARD_CLEAR -> settings = settings.copy(clipboardClearSeconds = value.asInt())
                VAULT_OTP_KEYRING -> keyring = decodeKeyring(value)
            }
        }

        val count = reader.readI32()
        if (count !in 0..MAX_ENTRIES) throw CorruptedVaultException("Invalid entry count")
        val entries = ArrayList<VaultEntry>(count)
        repeat(count) {
            var id: String? = null
            var title = ""
            var username = ""
            var password = ""
            var url = ""
            var notes = ""
            var createdAt = 0L
            var updatedAt = 0L
            var autofillTargets = emptyList<String>()
            var otp: SealedOtp? = null
            reader.readFields { tag, value ->
                when (tag) {
                    ENTRY_ID -> id = value.asString()
                    ENTRY_TITLE -> title = value.asString()
                    ENTRY_USERNAME -> username = value.asString()
                    ENTRY_PASSWORD -> password = value.asString()
                    ENTRY_URL -> url = value.asString()
                    ENTRY_NOTES -> notes = value.asString()
                    ENTRY_CREATED_AT -> createdAt = value.asLong()
                    ENTRY_UPDATED_AT -> updatedAt = value.asLong()
                    ENTRY_AUTOFILL_TARGETS -> autofillTargets = value.asString().split('\n').filter { it.isNotEmpty() }
                    ENTRY_OTP -> {
                        if (value.size !in AesGcm.OVERHEAD + 1..MAX_SEALED_OTP_SIZE) {
                            throw CorruptedVaultException("Invalid 2FA field")
                        }
                        otp = SealedOtp(value)
                    }
                }
            }
            entries += VaultEntry(
                id = id ?: throw CorruptedVaultException("Entry without id"),
                title = title,
                username = username,
                password = password,
                url = url,
                notes = notes,
                createdAt = createdAt,
                updatedAt = updatedAt,
                autofillTargets = autofillTargets,
                otp = otp,
            )
        }
        if (reader.remaining != 0) throw CorruptedVaultException("Trailing data after entries")
        return VaultData(settings, entries, keyring)
    }

    private fun encodeKeyring(keyring: OtpKeyring): ByteArray {
        val writer = ByteWriter(256)
        try {
            writer.putU16(6)
            writer.putBytesField(KEYRING_ID, keyring.id)
            writer.putIntField(KEYRING_MEMORY_KIB, keyring.kdfParams.memoryKiB)
            writer.putIntField(KEYRING_ITERATIONS, keyring.kdfParams.iterations)
            writer.putIntField(KEYRING_PARALLELISM, keyring.kdfParams.parallelism)
            writer.putBytesField(KEYRING_SALT, keyring.salt)
            writer.putBytesField(KEYRING_WRAPPED_KEY, keyring.wrappedKey)
            return writer.toByteArray()
        } finally {
            writer.wipe()
        }
    }

    private fun decodeKeyring(encoded: ByteArray): OtpKeyring {
        val reader = ByteReader(encoded)
        var id: ByteArray? = null
        var memoryKiB = 0
        var iterations = 0
        var parallelism = 0
        var salt: ByteArray? = null
        var wrappedKey: ByteArray? = null
        reader.readFields { tag, value ->
            when (tag) {
                KEYRING_ID -> id = value.copyOf()
                KEYRING_MEMORY_KIB -> memoryKiB = value.asInt()
                KEYRING_ITERATIONS -> iterations = value.asInt()
                KEYRING_PARALLELISM -> parallelism = value.asInt()
                KEYRING_SALT -> salt = value.copyOf()
                KEYRING_WRAPPED_KEY -> wrappedKey = value.copyOf()
            }
        }
        if (reader.remaining != 0) throw CorruptedVaultException("Trailing data in the 2FA keyring")
        val validId = id?.takeIf { it.size == OtpKeyring.ID_SIZE }
        val validSalt = salt?.takeIf { it.size in 16..64 }
        val validWrappedKey = wrappedKey?.takeIf { it.size == OtpKeyring.KEY_SIZE + AesGcm.OVERHEAD }
        if (validId == null || validSalt == null || validWrappedKey == null) {
            throw CorruptedVaultException("Invalid 2FA keyring")
        }
        if (!KdfParams.isValid(memoryKiB, iterations, parallelism)) {
            throw UnsupportedVaultException("2FA key derivation parameters out of range")
        }
        return OtpKeyring(validId, KdfParams(memoryKiB, iterations, parallelism), validSalt, validWrappedKey)
    }
}
