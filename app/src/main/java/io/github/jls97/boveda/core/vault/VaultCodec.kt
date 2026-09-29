package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.wipe

/**
 * Serializes [VaultData] to the plaintext that gets encrypted. Every record is a list of
 * tagged fields (`tag u16, length u32, value`), so newer versions can add fields and older
 * readers skip the tags they don't know.
 */
object VaultCodec {
    private const val PAYLOAD_VERSION = 1
    private const val MAX_ENTRIES = 100_000
    private const val MAX_FIELDS = 1_024

    private const val SETTING_AUTO_LOCK = 1
    private const val SETTING_CLIPBOARD_CLEAR = 2

    private const val ENTRY_ID = 1
    private const val ENTRY_TITLE = 2
    private const val ENTRY_USERNAME = 3
    private const val ENTRY_PASSWORD = 4
    private const val ENTRY_URL = 5
    private const val ENTRY_NOTES = 6
    private const val ENTRY_CREATED_AT = 7
    private const val ENTRY_UPDATED_AT = 8
    private const val ENTRY_AUTOFILL_TARGETS = 9

    fun encode(data: VaultData): ByteArray {
        val writer = ByteWriter(4_096)
        try {
            writer.putU16(PAYLOAD_VERSION)

            writer.putU16(2)
            writer.putIntField(SETTING_AUTO_LOCK, data.settings.autoLockSeconds)
            writer.putIntField(SETTING_CLIPBOARD_CLEAR, data.settings.clipboardClearSeconds)

            writer.putI32(data.entries.size)
            for (entry in data.entries) {
                writer.putU16(9)
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
        reader.readFields { tag, value ->
            when (tag) {
                SETTING_AUTO_LOCK -> settings = settings.copy(autoLockSeconds = value.asInt())
                SETTING_CLIPBOARD_CLEAR -> settings = settings.copy(clipboardClearSeconds = value.asInt())
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
            )
        }
        if (reader.remaining != 0) throw CorruptedVaultException("Trailing data after entries")
        return VaultData(settings, entries)
    }

    private fun ByteWriter.putStringField(tag: Int, value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        putU16(tag)
        putI32(encoded.size)
        putBytes(encoded)
        encoded.wipe()
    }

    private fun ByteWriter.putIntField(tag: Int, value: Int) {
        putU16(tag)
        putI32(4)
        putI32(value)
    }

    private fun ByteWriter.putLongField(tag: Int, value: Long) {
        putU16(tag)
        putI32(8)
        putI64(value)
    }

    /** Reads one record and hands every field to [onField]. The value buffer is wiped afterwards. */
    private inline fun ByteReader.readFields(onField: (tag: Int, value: ByteArray) -> Unit) {
        val fieldCount = readU16()
        if (fieldCount > MAX_FIELDS) throw CorruptedVaultException("Too many fields")
        repeat(fieldCount) {
            val tag = readU16()
            val value = readBytes(readI32())
            try {
                onField(tag, value)
            } finally {
                value.wipe()
            }
        }
    }

    private fun ByteArray.asString(): String = toString(Charsets.UTF_8)

    private fun ByteArray.asInt(): Int {
        if (size != 4) throw CorruptedVaultException("Invalid int field")
        return ByteReader(this).readI32()
    }

    private fun ByteArray.asLong(): Long {
        if (size != 8) throw CorruptedVaultException("Invalid long field")
        return ByteReader(this).readI64()
    }
}
