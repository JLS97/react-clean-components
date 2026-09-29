package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.crypto.randomBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultTest {

    // Cheap Argon2 costs keep the tests fast; the app always uses KdfParams.DEFAULT.
    private val testParams = KdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)

    private val sampleData = VaultData(
        settings = VaultSettings(autoLockSeconds = 300, clipboardClearSeconds = 15),
        entries = listOf(
            VaultEntry(
                id = "a1",
                title = "Banco",
                username = "yo@example.com",
                password = "p@ss\"word\\ñ€😀",
                url = "https://banco.example",
                notes = "Línea 1\nLínea 2",
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_100_000,
            ),
            VaultEntry(id = "b2", title = "", createdAt = 0, updatedAt = Long.MAX_VALUE),
        ),
    )

    @Test
    fun codecRoundTrip() {
        assertEquals(sampleData, VaultCodec.decode(VaultCodec.encode(sampleData)))
        assertEquals(VaultData(), VaultCodec.decode(VaultCodec.encode(VaultData())))
    }

    @Test
    fun codecSkipsUnknownFields() {
        val encoded = VaultCodec.encode(VaultData(entries = listOf(sampleData.entries[1])))
        // Settings record: version(2) + fieldCount(2) + 2 int fields (2 + 4 + 4 each).
        val settingsEnd = 2 + 2 + 2 * 10
        val withExtraField = encoded.copyOf().also {
            it[3] = 3 // declare one more settings field
        }
        val unknownField = byteArrayOf(0x7F, 0x7F, 0, 0, 0, 2, 9, 9)
        val patched = withExtraField.copyOfRange(0, settingsEnd) + unknownField +
            withExtraField.copyOfRange(settingsEnd, withExtraField.size)
        assertEquals(VaultData(entries = listOf(sampleData.entries[1])), VaultCodec.decode(patched))
    }

    @Test
    fun codecRejectsTruncatedData() {
        val encoded = VaultCodec.encode(sampleData)
        for (cut in listOf(1, 5, encoded.size / 2, encoded.size - 1)) {
            try {
                VaultCodec.decode(encoded.copyOf(cut))
                fail("Truncated payload of $cut bytes was accepted")
            } catch (expected: CorruptedVaultException) {
            }
        }
    }

    @Test
    fun codecKeepsAutofillTargets() {
        val entry = sampleData.entries[0].copy(autofillTargets = listOf("android:com.bank.app@${"b".repeat(64)}", "web:banco.es"))
        val data = VaultData(entries = listOf(entry))
        assertEquals(data, VaultCodec.decode(VaultCodec.encode(data)))
    }

    @Test
    fun codecReadsPhaseOneEntriesWithoutAutofillTargets() {
        // Phase 1 wrote 8 fields per entry. Rebuild that layout by dropping the (empty) 9th field:
        // version(2) + settings(2 + 2 * 10) + entry count(4) puts the entry's field count at byte 28.
        val entry = sampleData.entries[1]
        val current = VaultCodec.encode(VaultData(entries = listOf(entry)))
        val phaseOne = current.copyOf(current.size - 6).also { it[29] = 8 }
        assertEquals(VaultData(entries = listOf(entry)), VaultCodec.decode(phaseOne))
    }

    @Test(expected = CorruptedVaultException::class)
    fun codecRejectsTrailingData() {
        VaultCodec.decode(VaultCodec.encode(sampleData) + byteArrayOf(0))
    }

    @Test
    fun containerRoundTrip() {
        val created = VaultContainer.create("contraseña maestra".toCharArray(), sampleData, testParams)
        val blob = VaultContainer.seal(created.header, created.dek, created.data)

        val opened = VaultContainer.open(blob, "contraseña maestra".toCharArray())
        assertEquals(sampleData, opened.data)
        assertArrayEquals(created.dek, opened.dek)
        assertEquals(testParams, opened.header.kdfParams)

        val byKey = VaultContainer.openWithKey(blob, created.dek)
        assertEquals(sampleData, byKey.data)
    }

    @Test
    fun resealingUsesFreshCiphertext() {
        val created = VaultContainer.create("contraseña maestra".toCharArray(), sampleData, testParams)
        val first = VaultContainer.seal(created.header, created.dek, created.data)
        val second = VaultContainer.seal(created.header, created.dek, created.data)
        assertFalse(first.contentEquals(second))
    }

    @Test(expected = WrongPasswordException::class)
    fun containerRejectsWrongPassword() {
        val created = VaultContainer.create("contraseña maestra".toCharArray(), sampleData, testParams)
        val blob = VaultContainer.seal(created.header, created.dek, created.data)
        VaultContainer.open(blob, "contraseña maestrA".toCharArray())
    }

    @Test(expected = WrongPasswordException::class)
    fun containerDetectsTamperedSalt() {
        val created = VaultContainer.create("clave".toCharArray(), sampleData, testParams)
        val blob = VaultContainer.seal(created.header, created.dek, created.data)
        blob[20] = (blob[20].toInt() xor 1).toByte() // inside the salt
        VaultContainer.open(blob, "clave".toCharArray())
    }

    @Test(expected = CorruptedVaultException::class)
    fun containerDetectsTamperedBody() {
        val created = VaultContainer.create("clave".toCharArray(), sampleData, testParams)
        val blob = VaultContainer.seal(created.header, created.dek, created.data)
        blob[blob.size - 20] = (blob[blob.size - 20].toInt() xor 1).toByte()
        VaultContainer.open(blob, "clave".toCharArray())
    }

    @Test(expected = UnsupportedVaultException::class)
    fun containerRejectsAbsurdKdfCosts() {
        val created = VaultContainer.create("clave".toCharArray(), sampleData, testParams)
        val blob = VaultContainer.seal(created.header, created.dek, created.data)
        // memoryKiB lives right after magic, version and kdf id (bytes 6..9).
        blob[6] = 0x7F
        VaultContainer.open(blob, "clave".toCharArray())
    }

    @Test(expected = CorruptedVaultException::class)
    fun containerRejectsForeignFiles() {
        VaultContainer.open(ByteArray(200) { 7 }, "clave".toCharArray())
    }

    @Test
    fun changePasswordKeepsDataAndDek() {
        val created = VaultContainer.create("vieja".toCharArray(), sampleData, testParams)
        val newHeader = VaultContainer.changePassword(created.dek, "nueva".toCharArray(), testParams)
        val blob = VaultContainer.seal(newHeader, created.dek, created.data)

        assertEquals(sampleData, VaultContainer.open(blob, "nueva".toCharArray()).data)
        try {
            VaultContainer.open(blob, "vieja".toCharArray())
            fail("The old password still opened the vault")
        } catch (expected: WrongPasswordException) {
        }
    }

    @Test
    fun verifyPasswordChecksTheHeader() {
        val created = VaultContainer.create("buena".toCharArray(), sampleData, testParams)
        assertTrue(VaultContainer.verifyPassword(created.header, "buena".toCharArray()))
        assertFalse(VaultContainer.verifyPassword(created.header, "mala".toCharArray()))
    }

    @Test
    fun deviceLayerRoundTrip() {
        val key = randomBytes(32)
        val sealed = DeviceLayer.seal(key, "portable".toByteArray())
        assertTrue(sealed.size > "portable".length)
        assertArrayEquals("portable".toByteArray(), DeviceLayer.open(key, sealed))
    }

    @Test(expected = DeviceBindingException::class)
    fun deviceLayerRejectsOtherKeys() {
        val sealed = DeviceLayer.seal(randomBytes(32), "portable".toByteArray())
        DeviceLayer.open(randomBytes(32), sealed)
    }

    @Test(expected = CorruptedVaultException::class)
    fun deviceLayerRejectsUnknownFormat() {
        DeviceLayer.open(randomBytes(32), ByteArray(64))
    }
}
