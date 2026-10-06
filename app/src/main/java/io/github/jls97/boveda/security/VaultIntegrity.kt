package io.github.jls97.boveda.security

import android.annotation.SuppressLint
import android.content.Context
import java.security.MessageDigest

/**
 * Detects a vault file that was put back: after every write of `vault.bin` the SHA-256 of the
 * bytes written and a generation counter are kept outside the file, in SharedPreferences. When the
 * vault is opened, a file whose hash is not the recorded one is not the last one saved on this
 * phone (an older copy restored by hand, by root or by a forensic tool, which the Keystore layer
 * alone cannot tell apart). It only warns: a file whose hash matches, or a phone without a record
 * yet (first run after the update), opens normally.
 *
 * The algorithm is pure Kotlin over an [IntegrityStore] so it is unit-tested; the constructor with
 * a [Context] wires SharedPreferences.
 */
internal class VaultIntegrity(private val store: IntegrityStore) {
    constructor(context: Context) : this(PrefsStore(context))

    /** Remembers [vaultFile] as the last vault written on this phone. Call it right after writing. */
    fun recordWrite(vaultFile: ByteArray) {
        val previous = store.load()
        store.save(IntegrityRecord(sha256(vaultFile), (previous?.generation ?: 0L) + 1))
    }

    /**
     * True if [vaultFile] is the last vault written on this phone, or if nothing was recorded yet.
     * False means an older (or foreign) file took its place.
     */
    fun isLatest(vaultFile: ByteArray): Boolean {
        val record = store.load() ?: return true
        return MessageDigest.isEqual(record.sha256, sha256(vaultFile))
    }

    // commit() on purpose: the record must be on disk before the write it describes is reported.
    @SuppressLint("ApplySharedPref")
    private class PrefsStore(context: Context) : IntegrityStore {
        private val prefs = context.getSharedPreferences("vault_integrity", Context.MODE_PRIVATE)

        override fun load(): IntegrityRecord? {
            val hex = prefs.getString(KEY_SHA256, null) ?: return null
            val sha256 = hexToBytes(hex) ?: return null
            return IntegrityRecord(sha256, prefs.getLong(KEY_GENERATION, 0L))
        }

        override fun save(record: IntegrityRecord) {
            prefs.edit()
                .putString(KEY_SHA256, bytesToHex(record.sha256))
                .putLong(KEY_GENERATION, record.generation)
                .commit()
        }

        private companion object {
            const val KEY_SHA256 = "sha256"
            const val KEY_GENERATION = "generation"
        }
    }

    companion object {
        fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        private fun hexToBytes(hex: String): ByteArray? {
            if (hex.length % 2 != 0 || hex.isEmpty()) return null
            return try {
                hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

/** The last vault file written on this phone: its SHA-256 and how many writes there have been. */
class IntegrityRecord(sha256: ByteArray, val generation: Long) {
    private val digest = sha256.copyOf()

    val sha256: ByteArray get() = digest.copyOf()

    override fun toString() = "IntegrityRecord(generation=$generation)"
}

/** Where the integrity record is kept. Every write must be on disk before it returns. */
interface IntegrityStore {
    fun load(): IntegrityRecord?

    fun save(record: IntegrityRecord)
}
