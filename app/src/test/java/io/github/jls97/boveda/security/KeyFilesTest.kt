package io.github.jls97.boveda.security

import io.github.jls97.boveda.core.vault.OtpKeyring
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Serialization and parsing of layer.key, biometric.key (old and new format) and otp.key. */
class KeyFilesTest {

    private fun bytes(size: Int, seed: Int): ByteArray = ByteArray(size) { ((it * 7 + seed) and 0xFF).toByte() }

    private val iv = bytes(KEYSTORE_IV_SIZE, 1)

    // region layer.key

    @Test
    fun layerKeyFileRoundTrips() {
        val wrapped = bytes(DeviceKeyFile.WRAPPED_SIZE, 2)
        val encoded = DeviceKeyFile.encode(iv, wrapped)
        assertEquals(DeviceKeyFile.FILE_SIZE, encoded.size)
        assertEquals(12 + 32 + 16, encoded.size)
        val parsed = DeviceKeyFile.parse(encoded)
        assertNotNull(parsed)
        assertArrayEquals(iv, parsed!!.iv)
        assertArrayEquals(wrapped, parsed.wrappedKey)
        assertArrayEquals("boveda/device-layer-key/v1".toByteArray(), DeviceKeyFile.AAD)
    }

    @Test
    fun layerKeyFileRejectsOtherSizes() {
        assertNull(DeviceKeyFile.parse(ByteArray(0)))
        assertNull(DeviceKeyFile.parse(ByteArray(KEYSTORE_IV_SIZE)))
        assertNull(DeviceKeyFile.parse(ByteArray(DeviceKeyFile.FILE_SIZE - 1)))
        assertNull(DeviceKeyFile.parse(ByteArray(DeviceKeyFile.FILE_SIZE + 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun layerKeyFileRefusesAWrongIv() {
        DeviceKeyFile.encode(ByteArray(16), bytes(DeviceKeyFile.WRAPPED_SIZE, 2))
    }

    // endregion

    // region biometric.key

    @Test
    fun biometricKeyFileRoundTripsWithMagicVersionAliasAndAad() {
        val wrapped = bytes(BiometricKeyFile.WRAPPED_SIZE, 3)
        val encoded = BiometricKeyFile.encode(7, iv, wrapped)
        assertEquals(BiometricKeyFile.FILE_SIZE, encoded.size)
        assertEquals("BVBK", String(encoded.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals(1.toByte(), encoded[4])
        assertEquals(7.toByte(), encoded[5])
        val parsed = BiometricKeyFile.parse(encoded)
        assertNotNull(parsed)
        assertFalse(parsed!!.legacy)
        assertEquals(7, parsed.index)
        assertEquals("boveda.biometric.v1.7", parsed.alias)
        assertArrayEquals(iv, parsed.iv)
        assertArrayEquals(wrapped, parsed.wrappedKey)
        assertArrayEquals("boveda/biometric-dek/v1".toByteArray(), parsed.aad)
    }

    @Test
    fun biometricKeyFileStillReadsTheOldFormat() {
        // Before the magic existed the file was just IV | wrapped DEK, under a fixed alias and no AAD.
        val wrapped = bytes(BiometricKeyFile.WRAPPED_SIZE, 4)
        val old = iv + wrapped
        assertEquals(BiometricKeyFile.LEGACY_FILE_SIZE, old.size)
        val parsed = BiometricKeyFile.parse(old)
        assertNotNull(parsed)
        assertTrue(parsed!!.legacy)
        assertNull(parsed.index)
        assertEquals("boveda.biometric.v1", parsed.alias)
        assertNull("Un archivo antiguo se descifra sin AAD", parsed.aad)
        assertArrayEquals(iv, parsed.iv)
        assertArrayEquals(wrapped, parsed.wrappedKey)
    }

    @Test
    fun anOldBiometricFileStartingWithTheMagicIsStillOld() {
        // The old format starts with a random IV: the size tells the formats apart, not the magic.
        val ivLikeMagic = "BVBK".toByteArray() + bytes(KEYSTORE_IV_SIZE - 4, 9)
        val parsed = BiometricKeyFile.parse(ivLikeMagic + bytes(BiometricKeyFile.WRAPPED_SIZE, 5))
        assertNotNull(parsed)
        assertTrue(parsed!!.legacy)
        assertArrayEquals(ivLikeMagic, parsed.iv)
    }

    @Test
    fun biometricKeyFileRejectsWrongMagicVersionIndexOrSize() {
        val wrapped = bytes(BiometricKeyFile.WRAPPED_SIZE, 3)
        val good = BiometricKeyFile.encode(1, iv, wrapped)
        assertNull(BiometricKeyFile.parse(good.copyOf().also { it[0] = 'X'.code.toByte() }))
        assertNull(BiometricKeyFile.parse(good.copyOf().also { it[4] = 2 }))
        assertNull("índice 0 reservado", BiometricKeyFile.parse(good.copyOf().also { it[5] = 0 }))
        assertNull(BiometricKeyFile.parse(good.copyOfRange(0, good.size - 1)))
        assertNull(BiometricKeyFile.parse(good + byteArrayOf(0)))
        assertNull(BiometricKeyFile.parse(ByteArray(0)))
    }

    @Test
    fun biometricAliasIndexNeverRepeatsTheCurrentOne() {
        assertEquals(1, BiometricKeyFile.nextIndex(null)) // nothing yet, or an old-format file
        assertEquals(2, BiometricKeyFile.nextIndex(1))
        assertEquals(255, BiometricKeyFile.nextIndex(254))
        assertEquals(1, BiometricKeyFile.nextIndex(255))
        for (index in 1..255) {
            val next = BiometricKeyFile.nextIndex(index)
            assertTrue(next in 1..255)
            assertTrue(next != index)
            assertEquals("boveda.biometric.v1.$next", BiometricKeyFile.aliasOf(next))
            assertTrue(BiometricKeyFile.aliasOf(next).startsWith(BiometricKeyFile.ALIAS_PREFIX))
        }
        assertEquals(BiometricKeyFile.ALIAS_PREFIX, BiometricKeyFile.LEGACY_ALIAS)
    }

    @Test
    fun biometricIndexAbove255RoundTripsThroughTheByte() {
        val parsed = BiometricKeyFile.parse(BiometricKeyFile.encode(255, iv, bytes(BiometricKeyFile.WRAPPED_SIZE, 3)))
        assertEquals(255, parsed!!.index)
        assertEquals("boveda.biometric.v1.255", parsed.alias)
    }

    @Test(expected = IllegalArgumentException::class)
    fun biometricKeyFileRefusesAnIndexOutOfRange() {
        BiometricKeyFile.encode(256, iv, bytes(BiometricKeyFile.WRAPPED_SIZE, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun biometricKeyFileRefusesAWrongWrappedSize() {
        BiometricKeyFile.encode(1, iv, ByteArray(BiometricKeyFile.WRAPPED_SIZE + 1))
    }

    // endregion

    // region otp.key

    private val keyringId = bytes(OtpKeyring.ID_SIZE, 6)
    private val wrappedOtp = bytes(OtpKeyFile.WRAPPED_SIZE, 8)

    @Test
    fun otpKeyFileRoundTrips() {
        val encoded = OtpKeyFile.encode(OtpKeyFile.SLOT_B, keyringId, iv, wrappedOtp)
        assertEquals(OtpKeyFile.FILE_SIZE, encoded.size)
        assertEquals("BVOK", String(encoded.copyOfRange(0, 4), Charsets.US_ASCII))
        val parsed = OtpKeyFile.parse(encoded)
        assertNotNull(parsed)
        assertEquals(OtpKeyFile.SLOT_B, parsed!!.slot)
        assertEquals("boveda.otp.v1.b", parsed.alias)
        assertArrayEquals(keyringId, parsed.keyringId)
        assertArrayEquals(iv, parsed.iv)
        assertArrayEquals(wrappedOtp, parsed.wrappedKey)
    }

    @Test
    fun otpKeyFileWrittenBeforeTheSlotsIsSlotOne() {
        // The first format carried its version (1) where the slot is now: that is slot 1.
        val old = "BVOK".toByteArray() + byteArrayOf(1) + keyringId + iv + wrappedOtp
        val parsed = OtpKeyFile.parse(old)
        assertNotNull(parsed)
        assertEquals(OtpKeyFile.SLOT_A, parsed!!.slot)
        assertEquals("boveda.otp.v1", parsed.alias)
        assertArrayEquals(keyringId, parsed.keyringId)
    }

    @Test
    fun otpKeyFileRejectsWrongMagicSlotOrSize() {
        val good = OtpKeyFile.encode(OtpKeyFile.SLOT_A, keyringId, iv, wrappedOtp)
        assertNull(OtpKeyFile.parse(good.copyOf().also { it[0] = 'X'.code.toByte() }))
        assertNull(OtpKeyFile.parse(good.copyOf().also { it[4] = 3 }))
        assertNull(OtpKeyFile.parse(good.copyOf().also { it[4] = 0 }))
        assertNull(OtpKeyFile.parse(good.copyOfRange(0, good.size - 1)))
        assertNull(OtpKeyFile.parse(good + byteArrayOf(0)))
    }

    @Test
    fun otpSlotsAlternate() {
        assertEquals(OtpKeyFile.SLOT_A, OtpKeyFile.otherSlot(null))
        assertEquals(OtpKeyFile.SLOT_B, OtpKeyFile.otherSlot(OtpKeyFile.SLOT_A))
        assertEquals(OtpKeyFile.SLOT_A, OtpKeyFile.otherSlot(OtpKeyFile.SLOT_B))
    }

    @Test(expected = IllegalArgumentException::class)
    fun otpKeyFileRefusesAWrongKeyringId() {
        OtpKeyFile.encode(OtpKeyFile.SLOT_A, ByteArray(8), iv, wrappedOtp)
    }

    // endregion
}
