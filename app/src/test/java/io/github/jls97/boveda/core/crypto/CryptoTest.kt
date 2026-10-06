package io.github.jls97.boveda.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun deriveKeyMatchesTheReferenceImplementationVector() {
        // phc-winner-argon2, src/test.c: Argon2id v0x13, t=2, m=2^16 KiB, p=1, "password" / "somesalt".
        // Runs through Argon2Kdf.deriveKey itself, so a wrong variant, version or parameter order fails here.
        val key = Argon2Kdf.deriveKey("password".toCharArray(), "somesalt".toByteArray(), KdfParams(65_536, 2, 1))
        assertArrayEquals(hex("09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7"), key)
    }

    @Test
    fun deriveKeyMatchesAGeneratorConfiguredTheSameWay() {
        // Any drift between the wrapper and Argon2id (variant, version, memory/iterations swapped,
        // password encoding) shows up as a different key for the same inputs.
        val params = KdfParams(memoryKiB = 128, iterations = 2, parallelism = 2)
        val salt = ByteArray(16) { (it * 7).toByte() }
        val password = "contraseña ñandú €"
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
        val expected = ByteArray(Argon2Kdf.KEY_SIZE)
        generator.generateBytes(password.toByteArray(Charsets.UTF_8), expected)
        assertArrayEquals(expected, Argon2Kdf.deriveKey(password.toCharArray(), salt, params))
    }

    @Test
    fun kdfDefaultsAreTheDocumentedOnes() {
        // RFC 9106, second recommended option. The file format depends on these staying put.
        assertEquals(KdfParams(memoryKiB = 65_536, iterations = 3, parallelism = 4), KdfParams.DEFAULT)
        assertEquals(32, Argon2Kdf.KEY_SIZE)
    }

    @Test
    fun passwordEncodingIsUtf8() {
        assertArrayEquals(hex("61"), Argon2Kdf.encodePassword("a".toCharArray()))
        assertArrayEquals(hex("c3 b1"), Argon2Kdf.encodePassword("ñ".toCharArray()))
        assertArrayEquals(hex("e2 82 ac"), Argon2Kdf.encodePassword("€".toCharArray()))
        assertArrayEquals(hex("f0 9f 98 80"), Argon2Kdf.encodePassword("😀".toCharArray()))
        assertArrayEquals(ByteArray(0), Argon2Kdf.encodePassword(CharArray(0)))
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
    fun aesGcmMatchesTheGcmSpecificationTestVectors() {
        // McGrew & Viega, "The Galois/Counter Mode of Operation (GCM)", AES-256 test cases 15 and 16
        // (the NIST GCM vectors). The sealed form is nonce || ciphertext || tag with a 128-bit tag.
        val key = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        val nonce = hex("cafebabefacedbaddecaf888")
        val plaintext = hex(
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
                "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255",
        )
        val ciphertext = hex(
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad",
        )
        // Test case 15: no AAD.
        val sealed15 = nonce + ciphertext + hex("b094dac5d93471bdec1a502270e3cc6c")
        assertArrayEquals(plaintext, AesGcm.open(key, sealed15, ByteArray(0)))
        // Test case 16: 60 bytes of plaintext and 20 bytes of AAD, which must be authenticated.
        val aad = hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
        val sealed16 = nonce + ciphertext.copyOf(60) + hex("76fc6ece0f4e1768cddf8853bb2d551b")
        assertArrayEquals(plaintext.copyOf(60), AesGcm.open(key, sealed16, aad))
        try {
            AesGcm.open(key, sealed16, ByteArray(0))
            fail("The AAD of the test vector was not authenticated")
        } catch (expected: AuthenticationException) {
        }
        // A tag of fewer than 128 bits would be rejected: the whole tag is part of the format.
        try {
            AesGcm.open(key, sealed16.copyOf(sealed16.size - 4), aad)
            fail("A truncated tag was accepted")
        } catch (expected: AuthenticationException) {
        }
    }

    @Test
    fun aesGcmSealedSizeIsNoncePlusPlaintextPlusTag() {
        val key = randomBytes(AesGcm.KEY_SIZE)
        assertEquals(12, AesGcm.NONCE_SIZE)
        assertEquals(16, AesGcm.TAG_SIZE)
        for (size in listOf(0, 1, 15, 16, 17, 1_000)) {
            val sealed = AesGcm.seal(key, ByteArray(size) { 5 }, "aad".toByteArray())
            assertEquals(12 + size + 16, sealed.size)
            assertArrayEquals(ByteArray(size) { 5 }, AesGcm.open(key, sealed, "aad".toByteArray()))
        }
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
