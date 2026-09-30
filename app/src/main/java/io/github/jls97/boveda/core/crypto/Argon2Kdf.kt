package io.github.jls97.boveda.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.text.Normalizer

/** Argon2id (RFC 9106, version 0x13) cost parameters. */
data class KdfParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    init {
        require(isValid(memoryKiB, iterations, parallelism)) { "Argon2 parameters out of range" }
    }

    companion object {
        /** RFC 9106, second recommended option: 64 MiB of memory, 3 passes, 4 lanes. */
        val DEFAULT = KdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 4)

        /** Upper bounds keep a crafted file from exhausting memory or CPU when it is opened. */
        const val MAX_MEMORY_KIB = 256 * 1024
        const val MAX_ITERATIONS = 16
        const val MAX_PARALLELISM = 16

        fun isValid(memoryKiB: Int, iterations: Int, parallelism: Int): Boolean =
            parallelism in 1..MAX_PARALLELISM &&
                iterations in 1..MAX_ITERATIONS &&
                memoryKiB in 8 * parallelism..MAX_MEMORY_KIB
    }
}

/** Derives the key-encryption key from the master password with Argon2id. */
object Argon2Kdf {
    const val KEY_SIZE = 32

    fun deriveKey(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(salt)
                .build(),
        )
        val passwordBytes = encodePassword(password)
        return try {
            ByteArray(KEY_SIZE).also { generator.generateBytes(passwordBytes, it) }
        } finally {
            passwordBytes.wipe()
        }
    }

    /**
     * UTF-8 bytes of the password in Unicode NFC, so the same visible password always gives the
     * same key even if a keyboard composes accented letters differently.
     */
    fun encodePassword(password: CharArray): ByteArray {
        val chars = CharBuffer.wrap(password)
        val normalized = if (Normalizer.isNormalized(chars, Normalizer.Form.NFC)) {
            chars
        } else {
            CharBuffer.wrap(Normalizer.normalize(chars, Normalizer.Form.NFC))
        }
        val buffer = Charsets.UTF_8.encode(normalized)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) buffer.array().wipe()
        return bytes
    }
}
