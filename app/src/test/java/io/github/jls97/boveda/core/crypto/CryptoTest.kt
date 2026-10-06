package io.github.jls97.boveda.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {

    private fun hex(value: String): ByteArray =
        value.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun argon2idMatchesRfc9106TestVector() {
        // RFC 9106, section 5.3.
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(3)
                .withMemoryAsKB(32)
                .withParallelism(4)
                .withSalt(ByteArray(16) { 0x02 })
                .withSecret(ByteArray(8) { 0x03 })
                .withAdditional(ByteArray(12) { 0x04 })
                .build(),
        )
        val tag = ByteArray(32)
        generator.generateBytes(ByteArray(32) { 0x01 }, tag)
        assertArrayEquals(
            hex("0d 64 0d f5 8d 78 76 6c 08 c0 37 a3 4a 8b 53 c9 d0 1e f0 45 2d 75 b6 5e b5 25 20 e9 6b 01 e6 59"),
            tag,
        )
    }

    @Test
    fun deriveKeyIsDeterministicAndSaltDependent() {
        val params = KdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)
        val salt = ByteArray(32) { it.toByte() }
        val first = Argon2Kdf.deriveKey("correcto caballo".toCharArray(), salt, params)
        val second = Argon2Kdf.deriveKey("correcto caballo".toCharArray(), salt, params)
        val otherSalt = Argon2Kdf.deriveKey("correcto caballo".toCharArray(), ByteArray(32), params)
        assertEquals(32, first.size)
        assertArrayEquals(first, second)
        assertFalse(first.contentEquals(otherSalt))
    }

    @Test
    fun kdfParamsAreWeakerWithLessMemoryOrFewerPasses() {
        val default = KdfParams.DEFAULT
        assertFalse(default.isWeakerThan(default))
        assertTrue(KdfParams(memoryKiB = 8, iterations = 1, parallelism = 1).isWeakerThan(default))
        assertTrue(default.copy(memoryKiB = default.memoryKiB - 1).isWeakerThan(default))
        assertTrue(default.copy(iterations = default.iterations - 1).isWeakerThan(default))
        assertFalse(default.copy(memoryKiB = default.memoryKiB * 2).isWeakerThan(default))
        assertFalse(default.copy(iterations = default.iterations + 1).isWeakerThan(default))
        // More lanes are not a cheaper derivation on their own.
        assertFalse(default.copy(parallelism = default.parallelism * 2).isWeakerThan(default))
        assertEquals(64L * 1024 * 1024, default.memoryBytes)
    }

    @Test
    fun passwordEncodingNormalizesToNfc() {
        val composed = "ñandú".toCharArray()
        val decomposed = "ñandú".toCharArray()
        assertArrayEquals(Argon2Kdf.encodePassword(composed), Argon2Kdf.encodePassword(decomposed))
    }

    @Test
    fun aesGcmRoundTrip() {
        val key = randomBytes(AesGcm.KEY_SIZE)
        val aad = "header".toByteArray()
        val sealed = AesGcm.seal(key, "secreto".toByteArray(), aad)
        assertEquals(AesGcm.OVERHEAD + 7, sealed.size)
        assertArrayEquals("secreto".toByteArray(), AesGcm.open(key, sealed, aad))
    }

    @Test
    fun aesGcmUsesFreshNonces() {
        val key = randomBytes(AesGcm.KEY_SIZE)
        val first = AesGcm.seal(key, ByteArray(16), ByteArray(0))
        val second = AesGcm.seal(key, ByteArray(16), ByteArray(0))
        assertFalse(first.contentEquals(second))
    }

    @Test(expected = AuthenticationException::class)
    fun aesGcmRejectsTamperedCiphertext() {
        val key = randomBytes(AesGcm.KEY_SIZE)
        val sealed = AesGcm.seal(key, "secreto".toByteArray(), ByteArray(0))
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()
        AesGcm.open(key, sealed, ByteArray(0))
    }

    @Test(expected = AuthenticationException::class)
    fun aesGcmRejectsWrongAad() {
        val key = randomBytes(AesGcm.KEY_SIZE)
        val sealed = AesGcm.seal(key, "secreto".toByteArray(), "a".toByteArray())
        AesGcm.open(key, sealed, "b".toByteArray())
    }

    @Test(expected = AuthenticationException::class)
    fun aesGcmRejectsWrongKey() {
        val sealed = AesGcm.seal(randomBytes(AesGcm.KEY_SIZE), "secreto".toByteArray(), ByteArray(0))
        AesGcm.open(randomBytes(AesGcm.KEY_SIZE), sealed, ByteArray(0))
    }

    @Test(expected = AuthenticationException::class)
    fun aesGcmRejectsTruncatedInput() {
        AesGcm.open(randomBytes(AesGcm.KEY_SIZE), ByteArray(AesGcm.OVERHEAD - 1), ByteArray(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aesGcmRefusesToSealWithAnAllZeroKey() {
        // A key of all zeros is what wipe() leaves behind: sealing with it must never produce a file.
        AesGcm.seal(ByteArray(AesGcm.KEY_SIZE), "secreto".toByteArray(), ByteArray(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aesGcmRefusesToOpenWithAnAllZeroKey() {
        val sealed = AesGcm.seal(randomBytes(AesGcm.KEY_SIZE), "secreto".toByteArray(), ByteArray(0))
        AesGcm.open(ByteArray(AesGcm.KEY_SIZE), sealed, ByteArray(0))
    }

    @Test
    fun aesGcmAcceptsAKeyWithASingleNonZeroByte() {
        val key = ByteArray(AesGcm.KEY_SIZE).also { it[31] = 1 }
        val sealed = AesGcm.seal(key, "secreto".toByteArray(), ByteArray(0))
        assertArrayEquals("secreto".toByteArray(), AesGcm.open(key, sealed, ByteArray(0)))
    }
}
