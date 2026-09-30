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
