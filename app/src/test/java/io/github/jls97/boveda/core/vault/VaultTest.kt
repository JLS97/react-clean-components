package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.crypto.randomBytes
import io.github.jls97.boveda.core.crypto.wipe
import io.github.jls97.boveda.core.otp.OtpCrypto
import io.github.jls97.boveda.core.otp.OtpParams
import io.github.jls97.boveda.core.otp.OtpSecret
import io.github.jls97.boveda.core.otp.RecoveryCode
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

    @Test
    fun codecKeepsTheOtpKeyringAndSealedSecrets() {
        val otpKey = OtpCrypto.newKey()
        val keyringId = OtpCrypto.newKeyringId()
        val keyring = OtpCrypto.createKeyring(otpKey, keyringId, RecoveryCode.generate(), testParams)
        val secret = OtpSecret(randomBytes(20), OtpParams(), "ACME", "yo@example.com")
        val entry = sampleData.entries[0].let { it.copy(otp = OtpCrypto.seal(otpKey, keyringId, it.id, secret)) }
        val data = sampleData.copy(entries = listOf(entry, sampleData.entries[1]), otpKeyring = keyring)

        val decoded = VaultCodec.decode(VaultCodec.encode(data))
        assertEquals(data, decoded)
        assertEquals(secret, OtpCrypto.open(otpKey, keyringId, entry.id, decoded.entries[0].otp!!))
    }

    @Test
    fun codecWritesTheSameBytesWhenNo2faIsUsed() {
        // Vaults without 2FA keep the phase 1 and 2 layout: 2 settings fields and 9 per entry.
        val encoded = VaultCodec.encode(VaultData(entries = listOf(sampleData.entries[1])))
        assertEquals(2, encoded[3].toInt())
        assertEquals(9, encoded[29].toInt())
    }

    @Test(expected = CorruptedVaultException::class)
    fun codecRejectsADamagedOtpKeyring() {
        val keyring = OtpKeyring(ByteArray(15), testParams, randomBytes(32), randomBytes(OtpKeyring.KEY_SIZE + 28))
        VaultCodec.decode(VaultCodec.encode(VaultData(otpKeyring = keyring)))
    }

    @Test(expected = CorruptedVaultException::class)
    fun codecRejectsTrailingData() {
        VaultCodec.decode(VaultCodec.encode(sampleData) + byteArrayOf(0))
    }

    /** Builds a payload by hand, as a hostile file would: no settings, then one record per entry. */
    private fun rawPayload(settings: Map<Int, Int> = emptyMap(), entries: List<Map<Int, String>> = emptyList()): ByteArray {
        val writer = ByteWriter(64)
        writer.putU16(1)
        writer.putU16(settings.size)
        for ((tag, value) in settings) writer.putIntField(tag, value)
        writer.putI32(entries.size)
        for (fields in entries) {
            writer.putU16(fields.size)
            for ((tag, value) in fields) writer.putStringField(tag, value)
        }
        return writer.toByteArray()
    }

    private val entryId = 1
    private val autoLockTag = 1
    private val clipboardClearTag = 2

    @Test
    fun codecRejectsDuplicateEntryIds() {
        val payload = rawPayload(entries = listOf(mapOf(entryId to "a1"), mapOf(entryId to "a1")))
        try {
            VaultCodec.decode(payload)
            fail("Two entries with the same id were accepted")
        } catch (expected: CorruptedVaultException) {
        }
        // The same ids on different entries are fine.
        assertEquals(2, VaultCodec.decode(rawPayload(entries = listOf(mapOf(entryId to "a1"), mapOf(entryId to "a2")))).entries.size)
        // Nor does the app ever write such a file.
        val twin = sampleData.entries[1].copy(id = sampleData.entries[0].id)
        try {
            VaultCodec.encode(sampleData.copy(entries = sampleData.entries + twin))
            fail("Two entries with the same id were encoded")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun codecRejectsEmptyEntryIds() {
        try {
            VaultCodec.decode(rawPayload(entries = listOf(mapOf(entryId to ""))))
            fail("An entry with an empty id was accepted")
        } catch (expected: CorruptedVaultException) {
        }
        try {
            VaultCodec.encode(VaultData(entries = listOf(sampleData.entries[1].copy(id = ""))))
            fail("An entry with an empty id was encoded")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun codecEnforcesTheFieldLimitsWhenReadingAndWriting() {
        val limits = mapOf(
            2 to VaultCodec.MAX_TITLE_BYTES,
            3 to VaultCodec.MAX_USERNAME_BYTES,
            4 to VaultCodec.MAX_PASSWORD_BYTES,
            5 to VaultCodec.MAX_URL_BYTES,
            6 to VaultCodec.MAX_NOTES_BYTES,
            9 to VaultCodec.MAX_AUTOFILL_TARGETS_BYTES,
        )
        assertEquals(mapOf(2 to 1_024, 3 to 1_024, 4 to 4_096, 5 to 2_048, 6 to 65_536, 9 to 16_384), limits)
        for ((tag, limit) in limits) {
            val atLimit = rawPayload(entries = listOf(mapOf(entryId to "a1", tag to "x".repeat(limit))))
            assertEquals(1, VaultCodec.decode(atLimit).entries.size)
            try {
                VaultCodec.decode(rawPayload(entries = listOf(mapOf(entryId to "a1", tag to "x".repeat(limit + 1)))))
                fail("A field of tag $tag with ${limit + 1} bytes was accepted")
            } catch (expected: CorruptedVaultException) {
            }
        }
        // Multi-byte characters count in UTF-8 bytes, not in chars.
        try {
            VaultCodec.decode(rawPayload(entries = listOf(mapOf(entryId to "a1", 2 to "ñ".repeat(VaultCodec.MAX_TITLE_BYTES / 2 + 1)))))
            fail("A title above the limit in UTF-8 bytes was accepted")
        } catch (expected: CorruptedVaultException) {
        }
        try {
            VaultCodec.decode(rawPayload(entries = listOf(mapOf(entryId to "i".repeat(VaultCodec.MAX_ID_BYTES + 1)))))
            fail("An id above the limit was accepted")
        } catch (expected: CorruptedVaultException) {
        }

        val base = sampleData.entries[0]
        val tooLong = listOf(
            base.copy(title = "t".repeat(VaultCodec.MAX_TITLE_BYTES + 1)),
            base.copy(username = "u".repeat(VaultCodec.MAX_USERNAME_BYTES + 1)),
            base.copy(password = "p".repeat(VaultCodec.MAX_PASSWORD_BYTES + 1)),
            base.copy(url = "u".repeat(VaultCodec.MAX_URL_BYTES + 1)),
            base.copy(notes = "n".repeat(VaultCodec.MAX_NOTES_BYTES + 1)),
            base.copy(autofillTargets = List(VaultCodec.MAX_AUTOFILL_TARGETS_BYTES / 10 + 1) { "web:a$it.es" }),
            base.copy(id = "i".repeat(VaultCodec.MAX_ID_BYTES + 1)),
        )
        for (entry in tooLong) {
            try {
                VaultCodec.encode(VaultData(entries = listOf(entry)))
                fail("An oversized field was encoded")
            } catch (expected: IllegalArgumentException) {
            }
        }
        val atLimits = base.copy(
            title = "t".repeat(VaultCodec.MAX_TITLE_BYTES),
            notes = "n".repeat(VaultCodec.MAX_NOTES_BYTES),
            password = "p".repeat(VaultCodec.MAX_PASSWORD_BYTES),
        )
        assertEquals(atLimits, VaultCodec.decode(VaultCodec.encode(VaultData(entries = listOf(atLimits)))).entries[0])
    }

    @Test
    fun codecNormalizesSettingsToTheAllowedChoices() {
        fun decoded(autoLock: Int, clipboard: Int): VaultSettings =
            VaultCodec.decode(rawPayload(settings = mapOf(autoLockTag to autoLock, clipboardClearTag to clipboard))).settings

        // A negative auto-lock would never fire; a huge clipboard delay would never wipe.
        assertEquals(VaultSettings(autoLockSeconds = 0, clipboardClearSeconds = 15), decoded(-1, -5))
        assertEquals(VaultSettings(autoLockSeconds = 900, clipboardClearSeconds = 120), decoded(1_000_000, Int.MAX_VALUE))
        assertEquals(VaultSettings(autoLockSeconds = 0, clipboardClearSeconds = 15), decoded(Int.MIN_VALUE, Int.MIN_VALUE))
        assertEquals(VaultSettings(autoLockSeconds = 300, clipboardClearSeconds = 60), decoded(299, 70))
        // Values that are choices already stay as they are.
        for (autoLock in VaultSettings.AUTO_LOCK_CHOICES) {
            for (clipboard in VaultSettings.CLIPBOARD_CLEAR_CHOICES) {
                assertEquals(VaultSettings(autoLock, clipboard), decoded(autoLock, clipboard))
            }
        }
        val normalized = VaultCodec.decode(rawPayload(settings = mapOf(autoLockTag to -1))).settings
        assertTrue(normalized.autoLockSeconds in VaultSettings.AUTO_LOCK_CHOICES)
        assertTrue(normalized.clipboardClearSeconds in VaultSettings.CLIPBOARD_CLEAR_CHOICES)
    }

    @Test
    fun byteWriterGrowsFromAnEmptyBufferAndRejectsOverflow() {
        val writer = ByteWriter(0)
        writer.putU8(1)
        writer.putBytes(ByteArray(100) { 2 })
        writer.putI64(3)
        val written = writer.toByteArray()
        assertEquals(109, written.size)
        assertEquals(1, written[0].toInt())
        assertEquals(3, written[108].toInt())

        for (extra in listOf(Int.MAX_VALUE, Int.MAX_VALUE - 8, -1)) {
            try {
                writer.ensureCapacity(extra)
                fail("A growth of $extra bytes was accepted")
            } catch (expected: IllegalArgumentException) {
            }
        }
        // The writer is still usable after refusing.
        writer.putU8(4)
        assertEquals(110, writer.toByteArray().size)
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
    fun containerSealsWithARealDekButNotWithAWipedOne() {
        val created = VaultContainer.create("contraseña maestra".toCharArray(), sampleData, testParams)
        val blob = VaultContainer.seal(created.header, created.dek, created.data)
        assertEquals(sampleData, VaultContainer.openWithKey(blob, created.dek).data)

        // After lock() the DEK is all zeros: sealing must fail before anything reaches the disk.
        created.dek.wipe()
        try {
            VaultContainer.seal(created.header, created.dek, created.data)
            fail("The container sealed the vault with a wiped DEK")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun deviceLayerRefusesAWipedKey() {
        DeviceLayer.seal(ByteArray(32), "portable".toByteArray())
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

    @Test
    fun containerRejectsKdfCostsAboveTheMemoryOfTheProcess() {
        val created = VaultContainer.create("clave".toCharArray(), sampleData, testParams)
        val blob = VaultContainer.seal(created.header, created.dek, created.data)
        // The 64 KiB of testParams do not fit in half of a 64 KiB heap: refused before Argon2 runs.
        try {
            VaultContainer.open(blob, "clave".toCharArray(), maxHeapBytes = 64 * 1024L)
            fail("A KDF above the memory of the process was run")
        } catch (expected: KdfMemoryException) {
            assertTrue(expected is UnsupportedVaultException)
        }
        assertEquals(sampleData, VaultContainer.open(blob, "clave".toCharArray(), maxHeapBytes = 128 * 1024L).data)

        // The default costs (64 MiB) need a heap of at least 128 MiB; the hard cap holds regardless.
        VaultContainer.ensureKdfFitsInMemory(KdfParams.DEFAULT, 128L * 1024 * 1024)
        try {
            VaultContainer.ensureKdfFitsInMemory(KdfParams.DEFAULT, 128L * 1024 * 1024 - 1)
            fail("The default costs were accepted for a heap too small for them")
        } catch (expected: KdfMemoryException) {
        }
        val cap = KdfParams(KdfParams.MAX_MEMORY_KIB, 1, 1)
        VaultContainer.ensureKdfFitsInMemory(cap, Long.MAX_VALUE)
    }

    @Test
    fun containerReportsADamagedHeaderAsDamagedNotAsWrongPassword() {
        val created = VaultContainer.create("clave".toCharArray(), sampleData, testParams)
        val blob = VaultContainer.seal(created.header, created.dek, created.data)
        // Salt length lives after magic(4), version, kdf, memory(4), iterations(4) and parallelism.
        val badSalt = blob.copyOf().also { it[15] = 8 }
        try {
            VaultContainer.open(badSalt, "clave".toCharArray())
            fail("A header with an invalid salt length was accepted")
        } catch (expected: CorruptedVaultException) {
        }
        // KDF parameters outside the range are not a wrong password either (iterations at bytes 10..13).
        val badKdf = blob.copyOf().also { it[13] = 0 }
        try {
            VaultContainer.open(badKdf, "clave".toCharArray())
            fail("A header with iterations out of range was accepted")
        } catch (expected: UnsupportedVaultException) {
        }
    }

    @Test(expected = CorruptedVaultException::class)
    fun containerRejectsForeignFiles() {
        VaultContainer.open(ByteArray(200) { 7 }, "clave".toCharArray())
    }

    @Test
    fun changePasswordKeepsDataAndRotatesTheDek() {
        val created = VaultContainer.create("vieja".toCharArray(), sampleData, testParams)
        val oldBlob = VaultContainer.seal(created.header, created.dek, created.data)
        val rekeyed = VaultContainer.changePassword("nueva".toCharArray(), created.data, testParams)
        val blob = VaultContainer.seal(rekeyed.header, rekeyed.dek, rekeyed.data)

        assertFalse("The DEK must change with the password", created.dek.contentEquals(rekeyed.dek))
        val opened = VaultContainer.open(blob, "nueva".toCharArray())
        assertEquals(sampleData, opened.data)
        assertArrayEquals(rekeyed.dek, opened.dek)
        try {
            VaultContainer.open(blob, "vieja".toCharArray())
            fail("The old password still opened the vault")
        } catch (expected: WrongPasswordException) {
        }
        // The attack of M-03: the DEK taken from an old backup opens nothing written after the change.
        try {
            VaultContainer.openWithKey(blob, created.dek)
            fail("The old DEK still opened the re-encrypted vault")
        } catch (expected: CorruptedVaultException) {
        }
        // Nor does the old body pass under the new header, with either key.
        val oldBodyNewHeader = rekeyed.header.encoded + oldBlob.copyOfRange(created.header.encoded.size, oldBlob.size)
        for (dek in listOf(rekeyed.dek, created.dek)) {
            try {
                VaultContainer.openWithKey(oldBodyNewHeader, dek)
                fail("An old body was accepted under the new header")
            } catch (expected: CorruptedVaultException) {
            }
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
