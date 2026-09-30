package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.wipe

/** Big-endian binary writer whose buffer can be wiped, because it holds decrypted secrets. */
internal class ByteWriter(initialCapacity: Int = 256) {
    private var buffer = ByteArray(initialCapacity)
    private var size = 0

    private fun ensureCapacity(extra: Int) {
        val needed = size + extra
        if (needed <= buffer.size) return
        var newSize = buffer.size * 2
        while (newSize < needed) newSize *= 2
        val grown = buffer.copyOf(newSize)
        buffer.wipe()
        buffer = grown
    }

    fun putU8(value: Int) {
        ensureCapacity(1)
        buffer[size++] = value.toByte()
    }

    fun putU16(value: Int) {
        ensureCapacity(2)
        buffer[size++] = (value ushr 8).toByte()
        buffer[size++] = value.toByte()
    }

    fun putI32(value: Int) {
        ensureCapacity(4)
        buffer[size++] = (value ushr 24).toByte()
        buffer[size++] = (value ushr 16).toByte()
        buffer[size++] = (value ushr 8).toByte()
        buffer[size++] = value.toByte()
    }

    fun putI64(value: Long) {
        ensureCapacity(8)
        for (shift in 56 downTo 0 step 8) buffer[size++] = (value ushr shift).toByte()
    }

    fun putBytes(value: ByteArray) {
        ensureCapacity(value.size)
        value.copyInto(buffer, size)
        size += value.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    fun wipe() {
        buffer.wipe()
        size = 0
    }
}

/** Big-endian binary reader that rejects reads past the end instead of returning garbage. */
internal class ByteReader(private val data: ByteArray) {
    var offset = 0
        private set

    val remaining: Int get() = data.size - offset

    private fun ensureAvailable(count: Int) {
        if (count < 0 || count > remaining) throw CorruptedVaultException("Unexpected end of data")
    }

    fun readU8(): Int {
        ensureAvailable(1)
        return data[offset++].toInt() and 0xFF
    }

    fun readU16(): Int {
        ensureAvailable(2)
        return ((data[offset++].toInt() and 0xFF) shl 8) or (data[offset++].toInt() and 0xFF)
    }

    fun readI32(): Int {
        ensureAvailable(4)
        var value = 0
        repeat(4) { value = (value shl 8) or (data[offset++].toInt() and 0xFF) }
        return value
    }

    fun readI64(): Long {
        ensureAvailable(8)
        var value = 0L
        repeat(8) { value = (value shl 8) or (data[offset++].toLong() and 0xFF) }
        return value
    }

    fun readBytes(count: Int): ByteArray {
        ensureAvailable(count)
        return data.copyOfRange(offset, offset + count).also { offset += count }
    }
}

/** Most fields a record may declare. Anything above is a corrupted or hostile file. */
internal const val MAX_FIELDS = 1_024

/** Writes one tagged field: `tag u16, length u32, value`. Every record of the vault is a list of them. */
internal fun ByteWriter.putBytesField(tag: Int, value: ByteArray) {
    putU16(tag)
    putI32(value.size)
    putBytes(value)
}

internal fun ByteWriter.putStringField(tag: Int, value: String) {
    val encoded = value.toByteArray(Charsets.UTF_8)
    try {
        putBytesField(tag, encoded)
    } finally {
        encoded.wipe()
    }
}

internal fun ByteWriter.putIntField(tag: Int, value: Int) {
    putU16(tag)
    putI32(4)
    putI32(value)
}

internal fun ByteWriter.putLongField(tag: Int, value: Long) {
    putU16(tag)
    putI32(8)
    putI64(value)
}

/** Reads one record and hands every field to [onField]. The value buffer is wiped afterwards. */
internal inline fun ByteReader.readFields(onField: (tag: Int, value: ByteArray) -> Unit) {
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

internal fun ByteArray.asString(): String = toString(Charsets.UTF_8)

internal fun ByteArray.asInt(): Int {
    if (size != 4) throw CorruptedVaultException("Invalid int field")
    return ByteReader(this).readI32()
}

internal fun ByteArray.asLong(): Long {
    if (size != 8) throw CorruptedVaultException("Invalid long field")
    return ByteReader(this).readI64()
}
