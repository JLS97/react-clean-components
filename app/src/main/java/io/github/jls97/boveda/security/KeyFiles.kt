package io.github.jls97.boveda.security

import io.github.jls97.boveda.core.vault.OtpKeyring

/**
 * Formats of the three key files this phone keeps next to the vault (`layer.key`, `biometric.key`
 * and `otp.key`). Only bytes in, bytes out: no Android, no Keystore, no disk, so the parsing and
 * the serialization can be tested on the JVM. The managers in this package do the Keystore work.
 */

/** Size of the GCM IV Android Keystore picks for every encryption. */
internal const val KEYSTORE_IV_SIZE = 12

/** Size of the GCM authentication tag. */
internal const val GCM_TAG_SIZE = 16

/** `layer.key`: `IV (12) | wrapped layer key (32 + 16)`, wrapped with AAD [AAD]. */
internal object DeviceKeyFile {
    const val KEY_SIZE = 32
    const val WRAPPED_SIZE = KEY_SIZE + GCM_TAG_SIZE
    const val FILE_SIZE = KEYSTORE_IV_SIZE + WRAPPED_SIZE
    val AAD: ByteArray get() = "boveda/device-layer-key/v1".toByteArray()

    class Parsed(val iv: ByteArray, val wrappedKey: ByteArray)

    fun encode(iv: ByteArray, wrappedKey: ByteArray): ByteArray {
        require(iv.size == KEYSTORE_IV_SIZE) { "IV de tamaño inesperado" }
        require(wrappedKey.size == WRAPPED_SIZE) { "Clave envuelta de tamaño inesperado" }
        return iv + wrappedKey
    }

    /** Null if the bytes are not a layer key file. */
    fun parse(bytes: ByteArray): Parsed? {
        if (bytes.size != FILE_SIZE) return null
        return Parsed(bytes.copyOfRange(0, KEYSTORE_IV_SIZE), bytes.copyOfRange(KEYSTORE_IV_SIZE, FILE_SIZE))
    }
}

/**
 * `biometric.key`: `"BVBK" | version u8 (1) | alias index u8 (1..255) | IV (12) | wrapped DEK (32 + 16)`,
 * wrapped with AAD [AAD]. The alias index names the Keystore key that wraps the copy
 * (`boveda.biometric.v1.<index>`): every enrollment creates a new key under the next index, so the
 * key the current copy needs is only dropped once the new copy is on disk.
 *
 * Files written before this format are the bare `IV (12) | wrapped DEK (32 + 16)` under the alias
 * `boveda.biometric.v1` and without AAD; they keep opening and are replaced on the next enrollment.
 */
internal object BiometricKeyFile {
    val MAGIC: ByteArray get() = byteArrayOf(0x42, 0x56, 0x42, 0x4B) // "BVBK"
    const val VERSION: Byte = 1
    const val DEK_SIZE = 32
    const val WRAPPED_SIZE = DEK_SIZE + GCM_TAG_SIZE
    const val LEGACY_FILE_SIZE = KEYSTORE_IV_SIZE + WRAPPED_SIZE
    /** Magic, version and alias index. */
    const val HEADER_SIZE = 4 + 1 + 1
    const val FILE_SIZE = HEADER_SIZE + LEGACY_FILE_SIZE
    const val MAX_INDEX = 255
    val AAD: ByteArray get() = "boveda/biometric-dek/v1".toByteArray()

    /** Alias of the copies written before the versioned aliases existed. */
    const val LEGACY_ALIAS = "boveda.biometric.v1"

    /** Every alias this manager ever creates starts with this (the legacy alias included). */
    const val ALIAS_PREFIX = LEGACY_ALIAS

    class Parsed(
        /** Keystore alias that wraps [wrappedKey]. */
        val alias: String,
        /** Alias index, or null for a legacy file. */
        val index: Int?,
        val iv: ByteArray,
        val wrappedKey: ByteArray,
    ) {
        val legacy: Boolean get() = index == null

        /** AAD the copy was wrapped with: none for a legacy file. */
        val aad: ByteArray? get() = if (legacy) null else AAD
    }

    fun aliasOf(index: Int): String {
        require(index in 1..MAX_INDEX) { "Índice de alias fuera de rango" }
        return "$ALIAS_PREFIX.$index"
    }

    /** Index for the next enrollment: always different from [current] (null for none or legacy). */
    fun nextIndex(current: Int?): Int = if (current == null || current >= MAX_INDEX) 1 else current + 1

    fun encode(index: Int, iv: ByteArray, wrappedKey: ByteArray): ByteArray {
        require(index in 1..MAX_INDEX) { "Índice de alias fuera de rango" }
        require(iv.size == KEYSTORE_IV_SIZE) { "IV de tamaño inesperado" }
        require(wrappedKey.size == WRAPPED_SIZE) { "Clave envuelta de tamaño inesperado" }
        return MAGIC + byteArrayOf(VERSION, index.toByte()) + iv + wrappedKey
    }

    /** Null if the bytes are neither a current nor a legacy biometric key file. */
    fun parse(bytes: ByteArray): Parsed? = when (bytes.size) {
        LEGACY_FILE_SIZE -> Parsed(
            LEGACY_ALIAS,
            null,
            bytes.copyOfRange(0, KEYSTORE_IV_SIZE),
            bytes.copyOfRange(KEYSTORE_IV_SIZE, LEGACY_FILE_SIZE),
        )
        FILE_SIZE -> {
            val index = bytes[5].toInt() and 0xFF
            if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC) || bytes[4] != VERSION || index == 0) {
                null
            } else {
                val ivEnd = HEADER_SIZE + KEYSTORE_IV_SIZE
                Parsed(aliasOf(index), index, bytes.copyOfRange(HEADER_SIZE, ivEnd), bytes.copyOfRange(ivEnd, FILE_SIZE))
            }
        }
        else -> null
    }
}

/**
 * `otp.key`: `"BVOK" | slot u8 | keyring id (16) | IV (12) | wrapped 2FA key (32 + 16)`, wrapped
 * with [io.github.jls97.boveda.core.otp.OtpCrypto.deviceAad]. The keyring id says which vault the
 * copy belongs to; the slot (1 or 2) says which of two Keystore aliases wraps it. Files written
 * before the slots existed carry a 1 there (their format version), which is slot 1.
 */
internal object OtpKeyFile {
    val MAGIC: ByteArray get() = byteArrayOf(0x42, 0x56, 0x4F, 0x4B) // "BVOK"
    const val SLOT_A: Byte = 1
    const val SLOT_B: Byte = 2
    const val WRAPPED_SIZE = OtpKeyring.KEY_SIZE + GCM_TAG_SIZE
    const val FILE_SIZE = 4 + 1 + OtpKeyring.ID_SIZE + KEYSTORE_IV_SIZE + WRAPPED_SIZE

    class Parsed(val slot: Byte, val keyringId: ByteArray, val iv: ByteArray, val wrappedKey: ByteArray) {
        val alias: String get() = aliasOf(slot)
    }

    /** Slot 1 keeps the alias of the first format, so copies written before the slots keep opening. */
    fun aliasOf(slot: Byte): String {
        require(slot == SLOT_A || slot == SLOT_B) { "Ranura desconocida" }
        return if (slot == SLOT_A) "boveda.otp.v1" else "boveda.otp.v1.b"
    }

    fun otherSlot(slot: Byte?): Byte = if (slot == SLOT_A) SLOT_B else SLOT_A

    fun encode(slot: Byte, keyringId: ByteArray, iv: ByteArray, wrappedKey: ByteArray): ByteArray {
        require(slot == SLOT_A || slot == SLOT_B) { "Ranura desconocida" }
        require(keyringId.size == OtpKeyring.ID_SIZE) { "Id de llavero de tamaño inesperado" }
        require(iv.size == KEYSTORE_IV_SIZE) { "IV de tamaño inesperado" }
        require(wrappedKey.size == WRAPPED_SIZE) { "Clave envuelta de tamaño inesperado" }
        return MAGIC + byteArrayOf(slot) + keyringId + iv + wrappedKey
    }

    /** Null if the bytes are not a 2FA key file. */
    fun parse(bytes: ByteArray): Parsed? {
        if (bytes.size != FILE_SIZE || !bytes.copyOfRange(0, 4).contentEquals(MAGIC)) return null
        val slot = bytes[4]
        if (slot != SLOT_A && slot != SLOT_B) return null
        var offset = 5
        fun next(size: Int) = bytes.copyOfRange(offset, offset + size).also { offset += size }
        return Parsed(slot, next(OtpKeyring.ID_SIZE), next(KEYSTORE_IV_SIZE), next(WRAPPED_SIZE))
    }
}
