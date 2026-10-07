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
    fun biometricIndex255SurvivesTheSignedByte() {
        // 255 es el índice máximo: en el archivo es el byte 0xFF, que leído con signo vale -1 y
        // debe recuperarse como 255. Por encima de 255 no hay índices: encode los rechaza
        // (biometricKeyFileRefusesAnIndexOutOfRange) y nextIndex vuelve al 1.
        val encoded = BiometricKeyFile.encode(255, iv, bytes(BiometricKeyFile.WRAPPED_SIZE, 3))
        assertEquals((-1).toByte(), encoded[5])
        val parsed = BiometricKeyFile.parse(encoded)
        assertEquals(255, parsed!!.index)
        assertEquals("boveda.biometric.v1.255", parsed.alias)
        assertEquals(255, BiometricKeyFile.MAX_INDEX)
        assertEquals(1, BiometricKeyFile.nextIndex(255))
    }

    @Test
    fun legacyBiometricKeyFileFromTheFirstReleaseStillParses() {
        // Bytes literales con la forma exacta que escribía la primera versión publicada
        // (BiometricKeyManager de 05b7c78): IV de 12 bytes y DEK envuelta de 48, sin cabecera.
        // Un cambio del lector que dejara de abrirlos apagaría la huella de quien ya la activó.
        val legacyFile = byteArrayOf(
            0x1b, 0x7e, 0x4f, 0x02, 0x66, 0x29.toByte(), 0x9a.toByte(), 0xc1.toByte(), 0x33, 0x08, 0xd4.toByte(), 0x5f,
            0xe8.toByte(), 0x77, 0x10, 0xa3.toByte(), 0x4c, 0x95.toByte(), 0x2e, 0xb0.toByte(), 0x61, 0xf9.toByte(), 0x0d, 0x58,
            0xc7.toByte(), 0x3a, 0x82.toByte(), 0x15, 0xde.toByte(), 0x6b, 0x24, 0x9f.toByte(), 0x41, 0xaa.toByte(), 0x07, 0xe3.toByte(),
            0x5c, 0x90.toByte(), 0x18, 0xcd.toByte(), 0x72, 0x3f, 0xb6.toByte(), 0x05, 0x8e.toByte(), 0x4a, 0xd1.toByte(), 0x2b,
            0x6d, 0xf4.toByte(), 0x37, 0x80.toByte(), 0x1c, 0xa9.toByte(), 0x53, 0xee.toByte(), 0x0a, 0x76, 0xbc.toByte(), 0x45,
        )
        assertEquals(60, legacyFile.size)
        val parsed = BiometricKeyFile.parse(legacyFile)
        assertNotNull(parsed)
        assertTrue(parsed!!.legacy)
        assertNull(parsed.index)
        assertNull(parsed.aad)
        assertEquals("boveda.biometric.v1", parsed.alias)
        assertArrayEquals(legacyFile.copyOfRange(0, 12), parsed.iv)
        assertArrayEquals(legacyFile.copyOfRange(12, 60), parsed.wrappedKey)
        // La siguiente inscripción empieza en el índice 1, bajo un alias distinto del heredado.
        assertEquals(1, BiometricKeyFile.nextIndex(parsed.index))
        assertTrue(BiometricKeyFile.aliasOf(1) != parsed.alias)
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
    fun legacyOtpKeyFileFromTheFirstReleaseStillParses() {
        // Bytes literales con la forma exacta que escribía la primera versión publicada
        // (OtpKeyManager de 05b7c78): "BVOK", byte de versión 1, id del llavero (16), IV (12) y
        // clave 2FA envuelta (48). Hoy ese byte se lee como la ranura 1, con el alias de entonces.
        val legacyFile = byteArrayOf(
            0x42, 0x56, 0x4f, 0x4b, 0x01,
            0x10, 0x32, 0x54, 0x76, 0x98.toByte(), 0xba.toByte(), 0xdc.toByte(), 0xfe.toByte(), 0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xab.toByte(), 0xcd.toByte(), 0xef.toByte(),
            0x7a, 0x0c, 0xe5.toByte(), 0x93.toByte(), 0x2f, 0xb8.toByte(), 0x46, 0xd0.toByte(), 0x19, 0x6e, 0xa7.toByte(), 0x51,
            0x04, 0x9d.toByte(), 0x3b, 0xc2.toByte(), 0x68, 0xf1.toByte(), 0x27, 0x8c.toByte(), 0x5a, 0xe9.toByte(), 0x13, 0xb4.toByte(),
            0x7f, 0xd6.toByte(), 0x0e, 0x95.toByte(), 0x4b, 0xa0.toByte(), 0x36, 0xcf.toByte(), 0x62, 0x1d, 0xe7.toByte(), 0x88.toByte(),
            0x2c, 0xb3.toByte(), 0x59, 0xfa.toByte(), 0x05, 0x9e.toByte(), 0x44, 0xdb.toByte(), 0x71, 0x16, 0xac.toByte(), 0x63,
            0xf8.toByte(), 0x2a, 0x97.toByte(), 0x0f, 0xc4.toByte(), 0x5d, 0xb1.toByte(), 0x38, 0xe6.toByte(), 0x6f, 0x12, 0xa5.toByte(),
        )
        assertEquals(81, legacyFile.size)
        assertEquals(OtpKeyFile.FILE_SIZE, legacyFile.size)
        val parsed = OtpKeyFile.parse(legacyFile)
        assertNotNull(parsed)
        assertEquals(OtpKeyFile.SLOT_A, parsed!!.slot)
        assertEquals("boveda.otp.v1", parsed.alias)
        assertArrayEquals(legacyFile.copyOfRange(5, 21), parsed.keyringId)
        assertArrayEquals(legacyFile.copyOfRange(21, 33), parsed.iv)
        assertArrayEquals(legacyFile.copyOfRange(33, 81), parsed.wrappedKey)
        // Volver a escribirlo con el codificador actual da los mismos bytes: el formato no cambió.
        assertArrayEquals(legacyFile, OtpKeyFile.encode(parsed.slot, parsed.keyringId, parsed.iv, parsed.wrappedKey))
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
