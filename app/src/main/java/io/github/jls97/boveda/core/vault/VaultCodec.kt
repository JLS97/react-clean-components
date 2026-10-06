package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.AesGcm
import io.github.jls97.boveda.core.crypto.KdfParams
import kotlin.math.abs

/**
 * Serializes [VaultData] to the plaintext that gets encrypted. Every record is a list of
 * tagged fields (`tag u16, length u32, value`), so newer versions can add fields and older
 * readers skip the tags they don't know. The first record holds what applies to the whole vault:
 * the settings and, once 2FA is in use, its keyring.
 *
 * Compatibility policy. [PAYLOAD_VERSION] only changes when the layout itself changes, which no
 * older reader can cope with. New optional fields keep the payload version and raise
 * [MIN_READER_VERSION] instead: a reader that understands less than that refuses the vault, so
 * it can never open it, drop the fields it doesn't know and save the mutilated result over the
 * good one. Fields that an older reader may safely ignore are added without raising it.
 */
object VaultCodec {
    private const val PAYLOAD_VERSION = 1
    private const val MAX_ENTRIES = 100_000

    /** Highest [MIN_READER_VERSION] this codec understands. Raise it with every field it learns to keep. */
    const val READER_VERSION = 1

    /**
     * Lowest reader that keeps every field this codec writes, stored in the settings record. It
     * is 1 today, so every existing vault keeps opening; it must only grow when a new field would
     * be lost by a reader that doesn't know it.
     */
    const val MIN_READER_VERSION = 1

    private const val SETTING_AUTO_LOCK = 1
    private const val SETTING_CLIPBOARD_CLEAR = 2
    private const val VAULT_OTP_KEYRING = 3
    private const val SETTING_MIN_READER_VERSION = 4

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

    /**
     * Most UTF-8 bytes each text field may hold. Generous for anything typed by hand, and small
     * enough that a crafted backup cannot persist a value that freezes the interface or exhausts
     * memory on every unlock. Checked when encoding too, so the app never writes what it would
     * then refuse to read.
     */
    const val MAX_ID_BYTES = 128
    const val MAX_TITLE_BYTES = 1_024
    const val MAX_USERNAME_BYTES = 1_024
    const val MAX_PASSWORD_BYTES = 4_096
    const val MAX_URL_BYTES = 2_048
    const val MAX_NOTES_BYTES = 65_536
    const val MAX_AUTOFILL_TARGETS_BYTES = 16_384

    fun encode(data: VaultData): ByteArray {
        val writer = ByteWriter(4_096)
        try {
            writer.putU16(PAYLOAD_VERSION)

            val keyring = data.otpKeyring
            writer.putU16(if (keyring == null) 3 else 4)
            // First, so a reader too old for this vault stops before it parses anything else.
            writer.putIntField(SETTING_MIN_READER_VERSION, MIN_READER_VERSION)
            writer.putIntField(SETTING_AUTO_LOCK, data.settings.autoLockSeconds)
            writer.putIntField(SETTING_CLIPBOARD_CLEAR, data.settings.clipboardClearSeconds)
            if (keyring != null) writer.putBytesField(VAULT_OTP_KEYRING, encodeKeyring(keyring))

            writer.putI32(data.entries.size)
            val ids = HashSet<String>(data.entries.size * 2)
            for (entry in data.entries) {
                require(entry.id.isNotEmpty() && ids.add(entry.id)) { "Entry ids must be unique and not empty" }
                val otp = entry.otp
                writer.putU16(if (otp == null) 9 else 10)
                writer.putStringField(ENTRY_ID, entry.id, MAX_ID_BYTES)
                writer.putStringField(ENTRY_TITLE, entry.title, MAX_TITLE_BYTES)
                writer.putStringField(ENTRY_USERNAME, entry.username, MAX_USERNAME_BYTES)
                writer.putStringField(ENTRY_PASSWORD, entry.password, MAX_PASSWORD_BYTES)
                writer.putStringField(ENTRY_URL, entry.url, MAX_URL_BYTES)
                writer.putStringField(ENTRY_NOTES, entry.notes, MAX_NOTES_BYTES)
                writer.putLongField(ENTRY_CREATED_AT, entry.createdAt)
                writer.putLongField(ENTRY_UPDATED_AT, entry.updatedAt)
                // Package names and domains never contain line breaks.
                writer.putStringField(ENTRY_AUTOFILL_TARGETS, entry.autofillTargets.joinToString("\n"), MAX_AUTOFILL_TARGETS_BYTES)
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
                // Vaults from before this field carry no tag, which is version 1.
                SETTING_MIN_READER_VERSION -> {
                    val required = value.asInt()
                    if (required > READER_VERSION) {
                        throw UnsupportedVaultException("Vault needs reader version $required, this one is $READER_VERSION")
                    }
                }
                // A value outside the choices (negative, huge) would disable the lock or the clipboard
                // wipe: the file is not trusted on this, it only gets the closest choice.
                SETTING_AUTO_LOCK ->
                    settings = settings.copy(autoLockSeconds = nearestChoice(value.asInt(), VaultSettings.AUTO_LOCK_CHOICES))
                SETTING_CLIPBOARD_CLEAR ->
                    settings = settings.copy(clipboardClearSeconds = nearestChoice(value.asInt(), VaultSettings.CLIPBOARD_CLEAR_CHOICES))
                VAULT_OTP_KEYRING -> keyring = decodeKeyring(value)
            }
        }

        val count = reader.readI32()
        if (count !in 0..MAX_ENTRIES) throw CorruptedVaultException("Invalid entry count")
        val entries = ArrayList<VaultEntry>(count)
        val ids = HashSet<String>(count * 2)
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
                    ENTRY_ID -> id = value.asBoundedString(MAX_ID_BYTES)
                    ENTRY_TITLE -> title = value.asBoundedString(MAX_TITLE_BYTES)
                    ENTRY_USERNAME -> username = value.asBoundedString(MAX_USERNAME_BYTES)
                    ENTRY_PASSWORD -> password = value.asBoundedString(MAX_PASSWORD_BYTES)
                    ENTRY_URL -> url = value.asBoundedString(MAX_URL_BYTES)
                    ENTRY_NOTES -> notes = value.asBoundedString(MAX_NOTES_BYTES)
                    ENTRY_CREATED_AT -> createdAt = value.asLong()
                    ENTRY_UPDATED_AT -> updatedAt = value.asLong()
                    ENTRY_AUTOFILL_TARGETS ->
                        autofillTargets = value.asBoundedString(MAX_AUTOFILL_TARGETS_BYTES).split('\n').filter { it.isNotEmpty() }
                    ENTRY_OTP -> {
                        if (value.size !in AesGcm.OVERHEAD + 1..MAX_SEALED_OTP_SIZE) {
                            throw CorruptedVaultException("Invalid 2FA field")
                        }
                        otp = SealedOtp(value)
                    }
                }
            }
            val entryId = id ?: throw CorruptedVaultException("Entry without id")
            // The lists key their rows by id: a repeated one would crash the interface on every unlock.
            if (entryId.isEmpty() || !ids.add(entryId)) throw CorruptedVaultException("Empty or duplicate entry id")
            entries += VaultEntry(
                id = entryId,
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

    private fun ByteArray.asBoundedString(maxBytes: Int): String {
        if (size > maxBytes) throw CorruptedVaultException("Field too long")
        return asString()
    }

    /** The allowed value closest to [value], so a tampered setting can never weaken a control. */
    private fun nearestChoice(value: Int, choices: List<Int>): Int =
        choices.minBy { abs(it.toLong() - value) }

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
