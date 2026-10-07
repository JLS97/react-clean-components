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

    // Settings record: version(2) + fieldCount(2) + 3 int fields (2 + 4 + 4 each): reader version,
    // auto-lock and clipboard. Then entry count(4), so the first entry's field count sits at byte 38.
    private val settingsEnd = 2 + 2 + 3 * 10
    private val firstEntryFieldCountOffset = settingsEnd + 4 + 1

    @Test
    fun codecSkipsUnknownFields() {
        val encoded = VaultCodec.encode(VaultData(entries = listOf(sampleData.entries[1])))
        val withExtraField = encoded.copyOf().also {
            it[3] = 4 // declare one more settings field
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
        // Phase 1 wrote 8 fields per entry. Rebuild that layout by dropping the (empty) 9th field.
        val entry = sampleData.entries[1]
        val current = VaultCodec.encode(VaultData(entries = listOf(entry)))
        val phaseOne = current.copyOf(current.size - 6).also { it[firstEntryFieldCountOffset] = 8 }
        assertEquals(VaultData(entries = listOf(entry)), VaultCodec.decode(phaseOne))
    }

    @Test
    fun codecReadsVaultsWrittenBeforeTheReaderVersionField() {
        // Phase 1 to 3 settings records had only the two settings and no reader version: still version 1.
        val payload = rawPayload(settings = mapOf(autoLockTag to 300, clipboardClearTag to 15), entries = listOf(mapOf(entryId to "a1")))
        val decoded = VaultCodec.decode(payload)
        assertEquals(VaultSettings(autoLockSeconds = 300, clipboardClearSeconds = 15), decoded.settings)
        assertEquals(listOf("a1"), decoded.entries.map { it.id })
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
        // Vaults without 2FA keep the phase 1 and 2 entry layout (9 fields per entry) and the
        // settings record only gains the reader version: 3 fields.
        val encoded = VaultCodec.encode(VaultData(entries = listOf(sampleData.entries[1])))
        assertEquals(3, encoded[3].toInt())
        assertEquals(9, encoded[firstEntryFieldCountOffset].toInt())
    }

    @Test
    fun codecWritesTheMinimumReaderVersionFirstAndRejectsHigherOnes() {
        assertEquals(1, VaultCodec.MIN_READER_VERSION)
        assertEquals(1, VaultCodec.READER_VERSION)
        assertTrue(VaultCodec.MIN_READER_VERSION <= VaultCodec.READER_VERSION)
        // tag 4 (u16), length 4 (u32), value 1 (i32), right after the payload version and field count.
        val encoded = VaultCodec.encode(VaultData())
        assertArrayEquals(byteArrayOf(0, 1, 0, 3, 0, 4, 0, 0, 0, 4, 0, 0, 0, 1), encoded.copyOf(14))

        val minReaderTag = 4
        assertEquals(VaultData(), VaultCodec.decode(rawPayload(settings = mapOf(minReaderTag to 1))))
        assertEquals(VaultData(), VaultCodec.decode(rawPayload(settings = mapOf(minReaderTag to 0))))
        // A vault that a newer app marked as needing a newer reader is refused whole, so this
        // reader can never save it back without the fields it doesn't know.
        for (required in listOf(2, 100, Int.MAX_VALUE)) {
            try {
                VaultCodec.decode(rawPayload(settings = mapOf(minReaderTag to required, autoLockTag to 60)))
                fail("A vault needing reader version $required was opened")
            } catch (expected: UnsupportedVaultException) {
            }
        }
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
        // Format ceilings (R05-3): 256 KiB per field, 1 MiB for the notes. What a person may type
        // is bounded far lower by EntryLimits, which the editor and the autofill save enforce.
        assertEquals(mapOf(2 to 262_144, 3 to 262_144, 4 to 262_144, 5 to 262_144, 6 to 1_048_576, 9 to 262_144), limits)
        for ((tag, limit) in limits) {
            val atLimit = rawPayload(entries = listOf(mapOf(entryId to "a1", tag to "x".repeat(limit))))
            assertEquals(1, VaultCodec.decode(atLimit).entries.size)
            try {
                VaultCodec.decode(rawPayload(entries = listOf(mapOf(entryId to "a1", tag to "x".repeat(limit + 1)))))
                fail("A field of tag $tag with ${limit + 1} bytes was accepted")
            } catch (expected: OversizedFieldException) {
                // Not "damaged": the file is well formed, only too large for this version.
                assertTrue(expected is UnsupportedVaultException)
            }
        }
        // Multi-byte characters count in UTF-8 bytes, not in chars.
        try {
            VaultCodec.decode(rawPayload(entries = listOf(mapOf(entryId to "a1", 2 to "ñ".repeat(VaultCodec.MAX_TITLE_BYTES / 2 + 1)))))
            fail("A title above the limit in UTF-8 bytes was accepted")
        } catch (expected: OversizedFieldException) {
        }
        try {
            VaultCodec.decode(rawPayload(entries = listOf(mapOf(entryId to "i".repeat(VaultCodec.MAX_ID_BYTES + 1)))))
            fail("An id above the limit was accepted")
        } catch (expected: OversizedFieldException) {
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
                // Its own type, so the session can name the cause instead of "error interno".
                assertTrue(expected is FieldTooLongException)
            }
        }
        val atLimits = base.copy(
            title = "t".repeat(VaultCodec.MAX_TITLE_BYTES),
            notes = "n".repeat(VaultCodec.MAX_NOTES_BYTES),
            password = "p".repeat(VaultCodec.MAX_PASSWORD_BYTES),
        )
        assertEquals(atLimits, VaultCodec.decode(VaultCodec.encode(VaultData(entries = listOf(atLimits)))).entries[0])
    }

    /**
     * Vaults written by 0.1 and 0.2 had no field limits. One with a field above what the editor
     * accepts today (EntryLimits) but under the format ceiling must still open, and must save
     * again without the encoder refusing it (R05-3).
     */
    @Test
    fun oldVaultsWithFieldsAboveTheTypingLimitsStillOpenAndSaveAgain() {
        val notes = "n".repeat(70 * 1_024)
        val password = "p".repeat(EntryLimits.PASSWORD_BYTES + 1)
        val title = "ñ".repeat(EntryLimits.TITLE_BYTES)
        assertTrue(notes.length > EntryLimits.NOTES_BYTES && notes.length <= VaultCodec.MAX_NOTES_BYTES)
        assertTrue(EntryLimits.utf8Size(title) > EntryLimits.TITLE_BYTES)
        val old = rawPayload(entries = listOf(mapOf(entryId to "a1", 2 to title, 4 to password, 6 to notes)))

        val decoded = VaultCodec.decode(old)
        assertEquals(1, decoded.entries.size)
        assertEquals(notes, decoded.entries[0].notes)
        assertEquals(password, decoded.entries[0].password)
        assertEquals(title, decoded.entries[0].title)
        // Nothing is truncated on the way out either, and saving it again is not an error.
        assertEquals(decoded, VaultCodec.decode(VaultCodec.encode(decoded)))
        // The same entry is what the interface refuses to produce, with a message naming the field.
        assertEquals("El nombre supera 1 KB; recórtalo.", EntryLimits.oversizedField(decoded.entries[0]))
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

    private fun i32(value: Int): ByteArray = ByteWriter(4).apply { putI32(value) }.toByteArray()

    /** One record: a declared field count (by default the real one) and `tag, length, value` triples. */
    private fun rawRecord(vararg fields: Pair<Int, ByteArray>, declaredCount: Int = fields.size, lengthOverride: Int? = null): ByteArray {
        val writer = ByteWriter(64)
        writer.putU16(declaredCount)
        for ((tag, value) in fields) {
            writer.putU16(tag)
            writer.putI32(lengthOverride ?: value.size)
            writer.putBytes(value)
        }
        return writer.toByteArray()
    }

    /** A payload from raw records, so every byte of the layout is under the test's control. */
    private fun rawBytes(version: Int = 1, settings: ByteArray = rawRecord(), entryCount: Int, entries: List<ByteArray>): ByteArray {
        val writer = ByteWriter(64)
        writer.putU16(version)
        writer.putBytes(settings)
        writer.putI32(entryCount)
        for (entry in entries) writer.putBytes(entry)
        return writer.toByteArray()
    }

    private val entryOtpTag = 10
    private val keyringTag = 3

    private inline fun <reified T : VaultException> expect(what: String, block: () -> Unit) {
        try {
            block()
            fail("$what was accepted")
        } catch (e: VaultException) {
            if (e !is T) fail("$what: expected ${T::class.simpleName}, got ${e::class.simpleName}")
        }
    }

    @Test
    fun codecRejectsHostilePayloads() {
        val entry = rawRecord(entryId to "a1".toByteArray())
        // Sanity: the builder produces what the codec reads.
        assertEquals(listOf("a1"), VaultCodec.decode(rawBytes(entryCount = 1, entries = listOf(entry))).entries.map { it.id })

        expect<UnsupportedVaultException>("a payload of a future version") { VaultCodec.decode(rawBytes(version = 2, entryCount = 0, entries = emptyList())) }
        expect<UnsupportedVaultException>("a payload of version 0") { VaultCodec.decode(rawBytes(version = 0, entryCount = 0, entries = emptyList())) }
        expect<CorruptedVaultException>("a negative entry count") { VaultCodec.decode(rawBytes(entryCount = -1, entries = emptyList())) }
        expect<CorruptedVaultException>("100 001 entries") { VaultCodec.decode(rawBytes(entryCount = 100_001, entries = emptyList())) }
        expect<CorruptedVaultException>("more entries than records") { VaultCodec.decode(rawBytes(entryCount = 2, entries = listOf(entry))) }
        expect<CorruptedVaultException>("1 025 fields") {
            VaultCodec.decode(rawBytes(settings = rawRecord(declaredCount = 1_025), entryCount = 0, entries = emptyList()))
        }
        expect<CorruptedVaultException>("a field length of 0xFFFFFFFF") {
            VaultCodec.decode(rawBytes(settings = rawRecord(autoLockTag to i32(60), lengthOverride = -1), entryCount = 0, entries = emptyList()))
        }
        expect<CorruptedVaultException>("a field longer than what remains") {
            VaultCodec.decode(rawBytes(entryCount = 1, entries = listOf(rawRecord(entryId to "a1".toByteArray(), lengthOverride = 3 + 4 + 1))))
        }
        expect<CorruptedVaultException>("an int field of 3 bytes") {
            VaultCodec.decode(rawBytes(settings = rawRecord(autoLockTag to byteArrayOf(0, 0, 60)), entryCount = 0, entries = emptyList()))
        }
        expect<CorruptedVaultException>("a long field of 4 bytes") {
            VaultCodec.decode(rawBytes(entryCount = 1, entries = listOf(rawRecord(entryId to "a1".toByteArray(), 7 to i32(1)))))
        }
        expect<CorruptedVaultException>("an entry without id") {
            VaultCodec.decode(rawBytes(entryCount = 1, entries = listOf(rawRecord(2 to "Banco".toByteArray()))))
        }
        expect<CorruptedVaultException>("an empty payload") { VaultCodec.decode(ByteArray(0)) }
        expect<CorruptedVaultException>("a payload cut inside the entry count") { VaultCodec.decode(rawBytes(entryCount = 0, entries = emptyList()).copyOf(5)) }

        // A sealed 2FA secret is at least a nonce, a tag and one byte, and never above 4 096 bytes.
        fun withOtp(size: Int) = rawBytes(entryCount = 1, entries = listOf(rawRecord(entryId to "a1".toByteArray(), entryOtpTag to ByteArray(size) { 1 })))
        expect<CorruptedVaultException>("a 2FA field of 28 bytes") { VaultCodec.decode(withOtp(28)) }
        expect<CorruptedVaultException>("a 2FA field of 4 097 bytes") { VaultCodec.decode(withOtp(4_097)) }
        assertEquals(29, VaultCodec.decode(withOtp(29)).entries[0].otp!!.bytes.size)
        assertEquals(4_096, VaultCodec.decode(withOtp(4_096)).entries[0].otp!!.bytes.size)
    }

    @Test
    fun codecRejectsHostileKeyrings() {
        val good = mapOf(
            1 to ByteArray(16) { 1 }, 2 to i32(64), 3 to i32(1), 4 to i32(1),
            5 to ByteArray(32) { 2 }, 6 to ByteArray(OtpKeyring.KEY_SIZE + 28) { 3 },
        )
        fun payload(keyring: ByteArray) = rawBytes(settings = rawRecord(keyringTag to keyring), entryCount = 0, entries = emptyList())
        fun keyringWith(vararg changes: Pair<Int, ByteArray>): ByteArray =
            rawRecord(*(good + changes).entries.map { it.key to it.value }.toTypedArray())

        val decoded = VaultCodec.decode(payload(keyringWith())).otpKeyring!!
        assertEquals(OtpKeyring(ByteArray(16) { 1 }, KdfParams(64, 1, 1), ByteArray(32) { 2 }, ByteArray(60) { 3 }), decoded)
        assertEquals(16, VaultCodec.decode(payload(keyringWith(5 to ByteArray(16)))).otpKeyring!!.salt.size)
        assertEquals(64, VaultCodec.decode(payload(keyringWith(5 to ByteArray(64)))).otpKeyring!!.salt.size)

        expect<CorruptedVaultException>("a keyring salt of 15 bytes") { VaultCodec.decode(payload(keyringWith(5 to ByteArray(15)))) }
        expect<CorruptedVaultException>("a keyring salt of 65 bytes") { VaultCodec.decode(payload(keyringWith(5 to ByteArray(65)))) }
        expect<CorruptedVaultException>("a keyring id of 17 bytes") { VaultCodec.decode(payload(keyringWith(1 to ByteArray(17)))) }
        expect<CorruptedVaultException>("a wrapped 2FA key of 59 bytes") { VaultCodec.decode(payload(keyringWith(6 to ByteArray(59)))) }
        expect<CorruptedVaultException>("a keyring without wrapped key") { VaultCodec.decode(payload(rawRecord(*(good - 6).map { it.key to it.value }.toTypedArray()))) }
        expect<CorruptedVaultException>("trailing data in the keyring") { VaultCodec.decode(payload(keyringWith() + byteArrayOf(0))) }
        expect<UnsupportedVaultException>("keyring memory of 4 KiB") { VaultCodec.decode(payload(keyringWith(2 to i32(4)))) }
        expect<UnsupportedVaultException>("keyring memory above the cap") { VaultCodec.decode(payload(keyringWith(2 to i32(KdfParams.MAX_MEMORY_KIB + 1)))) }
        expect<UnsupportedVaultException>("keyring with 0 iterations") { VaultCodec.decode(payload(keyringWith(3 to i32(0)))) }
        expect<UnsupportedVaultException>("keyring with 17 iterations") { VaultCodec.decode(payload(keyringWith(3 to i32(17)))) }
        expect<UnsupportedVaultException>("keyring with 0 lanes") { VaultCodec.decode(payload(keyringWith(4 to i32(0)))) }
        expect<UnsupportedVaultException>("keyring with 17 lanes") { VaultCodec.decode(payload(keyringWith(4 to i32(17)))) }
        expect<CorruptedVaultException>("a keyring record cut short") { VaultCodec.decode(payload(keyringWith().copyOf(10))) }
    }

    /** Writes a header field by field, as a hostile file would, so no test depends on offsets. */
    private fun rawHeader(
        version: Int = 1,
        kdf: Int = 1,
        memoryKiB: Int = testParams.memoryKiB,
        iterations: Int = testParams.iterations,
        parallelism: Int = testParams.parallelism,
        salt: ByteArray = ByteArray(32) { 1 },
        saltLength: Int = salt.size,
        wrappedDek: ByteArray = ByteArray(32 + 28) { 2 }, // DEK plus nonce and tag
        wrappedLength: Int = wrappedDek.size,
        body: ByteArray = ByteArray(28) { 3 },
    ): ByteArray {
        val writer = ByteWriter(128)
        writer.putBytes("BOVD".toByteArray())
        writer.putU8(version)
        writer.putU8(kdf)
        writer.putI32(memoryKiB)
        writer.putI32(iterations)
        writer.putU8(parallelism)
        writer.putU8(saltLength)
        writer.putBytes(salt)
        writer.putU8(wrappedLength)
        writer.putBytes(wrappedDek)
        writer.putBytes(body)
        return writer.toByteArray()
    }

    @Test
    fun containerRejectsHostileHeaders() {
        // Sanity: the builder produces a header the container parses, with every length at its limit.
        assertEquals(testParams, VaultContainer.parseHeader(rawHeader()).kdfParams)
        VaultContainer.parseHeader(rawHeader(salt = ByteArray(16)))
        VaultContainer.parseHeader(rawHeader(salt = ByteArray(64)))

        expect<UnsupportedVaultException>("a vault of a future format version") { VaultContainer.parseHeader(rawHeader(version = 2)) }
        expect<UnsupportedVaultException>("a vault of format version 0") { VaultContainer.parseHeader(rawHeader(version = 0)) }
        expect<UnsupportedVaultException>("an unknown key derivation") { VaultContainer.parseHeader(rawHeader(kdf = 2)) }
        expect<UnsupportedVaultException>("key derivation 0") { VaultContainer.parseHeader(rawHeader(kdf = 0)) }
        expect<CorruptedVaultException>("a salt of 15 bytes") { VaultContainer.parseHeader(rawHeader(salt = ByteArray(15))) }
        expect<CorruptedVaultException>("a salt of 65 bytes") { VaultContainer.parseHeader(rawHeader(salt = ByteArray(65))) }
        expect<CorruptedVaultException>("a salt length of 0") { VaultContainer.parseHeader(rawHeader(saltLength = 0)) }
        // The wrapped DEK is exactly 60 bytes: 32 of key, 12 of nonce and 16 of tag.
        expect<CorruptedVaultException>("a wrapped DEK of 59 bytes") { VaultContainer.parseHeader(rawHeader(wrappedDek = ByteArray(59))) }
        expect<CorruptedVaultException>("a wrapped DEK of 61 bytes") { VaultContainer.parseHeader(rawHeader(wrappedDek = ByteArray(61))) }
        expect<CorruptedVaultException>("a wrapped DEK of 48 bytes") { VaultContainer.parseHeader(rawHeader(wrappedDek = ByteArray(48))) }
        expect<CorruptedVaultException>("a wrapped DEK of 32 bytes") { VaultContainer.parseHeader(rawHeader(wrappedDek = ByteArray(32))) }
        expect<CorruptedVaultException>("a wrapped DEK length of 255") { VaultContainer.parseHeader(rawHeader(wrappedLength = 255)) }
        expect<CorruptedVaultException>("a wrapped DEK length of 0") { VaultContainer.parseHeader(rawHeader(wrappedLength = 0)) }
        expect<CorruptedVaultException>("a body of 27 bytes") { VaultContainer.parseHeader(rawHeader(body = ByteArray(27))) }
        expect<CorruptedVaultException>("a header cut inside the salt") { VaultContainer.parseHeader(rawHeader().copyOf(20)) }
        expect<CorruptedVaultException>("a wrong magic") { VaultContainer.parseHeader(rawHeader().also { it[0] = 'X'.code.toByte() }) }
        expect<UnsupportedVaultException>("memory of 7 KiB") { VaultContainer.parseHeader(rawHeader(memoryKiB = 7)) }
        expect<UnsupportedVaultException>("memory above the cap") { VaultContainer.parseHeader(rawHeader(memoryKiB = KdfParams.MAX_MEMORY_KIB + 1)) }
        expect<UnsupportedVaultException>("0 iterations") { VaultContainer.parseHeader(rawHeader(iterations = 0)) }
        expect<UnsupportedVaultException>("17 iterations") { VaultContainer.parseHeader(rawHeader(iterations = 17)) }
        expect<UnsupportedVaultException>("0 lanes") { VaultContainer.parseHeader(rawHeader(parallelism = 0)) }
        expect<UnsupportedVaultException>("17 lanes") { VaultContainer.parseHeader(rawHeader(parallelism = 17)) }
        expect<UnsupportedVaultException>("less memory than 8 KiB per lane") { VaultContainer.parseHeader(rawHeader(memoryKiB = 64, parallelism = 9)) }

        // open() goes through the same validation before touching the password.
        expect<UnsupportedVaultException>("a future format on open") { VaultContainer.open(rawHeader(version = 2), "clave".toCharArray()) }
        expect<CorruptedVaultException>("a short salt on open") { VaultContainer.open(rawHeader(salt = ByteArray(15)), "clave".toCharArray()) }
        expect<CorruptedVaultException>("a short salt on openWithKey") { VaultContainer.openWithKey(rawHeader(salt = ByteArray(15)), randomBytes(32)) }
    }

    @Test
    fun bodyTransplantBetweenVaultsIsRejected() {
        // Two vaults with the same password: the body of one pasted under the header of the other
        // must fail, because the AAD of the body is the whole header.
        val first = VaultContainer.create("misma clave".toCharArray(), sampleData, testParams)
        val second = VaultContainer.create("misma clave".toCharArray(), VaultData(), testParams)
        val firstBlob = VaultContainer.seal(first.header, first.dek, first.data)
        val secondBlob = VaultContainer.seal(second.header, second.dek, second.data)
        val transplanted = first.header.encoded + secondBlob.copyOfRange(second.header.encoded.size, secondBlob.size)
        expect<CorruptedVaultException>("a body from another vault") { VaultContainer.open(transplanted, "misma clave".toCharArray()) }
        expect<CorruptedVaultException>("a body from another vault, by key") { VaultContainer.openWithKey(transplanted, first.dek) }
        expect<CorruptedVaultException>("a body from another vault, with its own key") { VaultContainer.openWithKey(transplanted, second.dek) }
        // The originals still open: nothing else was touched.
        assertEquals(sampleData, VaultContainer.open(firstBlob, "misma clave".toCharArray()).data)
    }

    @Test
    fun wrappedDekSwapIsRejected() {
        val first = VaultContainer.create("misma clave".toCharArray(), sampleData, testParams)
        val second = VaultContainer.create("misma clave".toCharArray(), sampleData, testParams)
        val secondBlob = VaultContainer.seal(second.header, second.dek, second.data)
        val headerSize = second.header.encoded.size
        val wrappedSize = 32 + 28
        // The wrapped DEK is the tail of the header: replace it with the other vault's.
        val swapped = secondBlob.copyOf().also {
            first.header.encoded.copyInto(it, headerSize - wrappedSize, headerSize - wrappedSize)
        }
        assertFalse(swapped.contentEquals(secondBlob))
        expect<WrongPasswordException>("a wrapped DEK from another vault") { VaultContainer.open(swapped, "misma clave".toCharArray()) }
    }

    @Test
    fun openWithKeyRejectsForeignDekAndLeavesItIntact() {
        val vault = VaultContainer.create("clave".toCharArray(), sampleData, testParams)
        val blob = VaultContainer.seal(vault.header, vault.dek, vault.data)
        val foreign = randomBytes(32)
        val copy = foreign.copyOf()
        expect<CorruptedVaultException>("a foreign DEK") { VaultContainer.openWithKey(blob, foreign) }
        // The caller's copy (the Keystore-wrapped one) is not wiped by the failed attempt.
        assertArrayEquals(copy, foreign)
        // Nor is the right one, when the body fails for other reasons.
        val own = vault.dek.copyOf()
        val damaged = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        expect<CorruptedVaultException>("a damaged body") { VaultContainer.openWithKey(damaged, own) }
        assertArrayEquals(vault.dek, own)
    }

    @Test
    fun headerFromOldPasswordDoesNotOpenNewBody() {
        val old = VaultContainer.create("vieja".toCharArray(), sampleData, testParams)
        val rekeyed = VaultContainer.changePassword("nueva".toCharArray(), old.data, testParams)
        val newBlob = VaultContainer.seal(rekeyed.header, rekeyed.dek, rekeyed.data)
        // An old header (whose password may be compromised) pasted on a current body.
        val spliced = old.header.encoded + newBlob.copyOfRange(rekeyed.header.encoded.size, newBlob.size)
        expect<CorruptedVaultException>("a current body under an old header") { VaultContainer.open(spliced, "vieja".toCharArray()) }
        expect<WrongPasswordException>("the new password on the old header") { VaultContainer.open(spliced, "nueva".toCharArray()) }
        expect<CorruptedVaultException>("the old DEK on the current body") { VaultContainer.openWithKey(spliced, old.dek) }
    }

    @Test
    fun deviceLayerRejectsAFutureVersionByte() {
        val key = randomBytes(32)
        val sealed = DeviceLayer.seal(key, "portable".toByteArray())
        assertEquals(1, sealed[4].toInt())
        val future = sealed.copyOf().also { it[4] = 2 }
        expect<CorruptedVaultException>("a device layer of version 2") { DeviceLayer.open(key, future) }
        val truncated = sealed.copyOf(4 + 28 - 1)
        expect<CorruptedVaultException>("a device layer too short for a tag") { DeviceLayer.open(key, truncated) }
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
        // A header asking for 65 MiB (above DEFAULT) does not fit in half of a 128 MiB heap:
        // refused before Argon2 runs. memoryKiB lives right after magic, version and kdf id (bytes 6..9).
        val aboveDefault = KdfParams(KdfParams.DEFAULT.memoryKiB + 1024, 3, 4)
        val hostile = blob.copyOf()
        hostile[6] = (aboveDefault.memoryKiB ushr 24).toByte()
        hostile[7] = (aboveDefault.memoryKiB ushr 16).toByte()
        hostile[8] = (aboveDefault.memoryKiB ushr 8).toByte()
        hostile[9] = aboveDefault.memoryKiB.toByte()
        try {
            VaultContainer.open(hostile, "clave".toCharArray(), maxHeapBytes = 128L * 1024 * 1024)
            fail("A KDF above the memory of the process was run")
        } catch (expected: KdfMemoryException) {
            assertTrue(expected is UnsupportedVaultException)
        }
        // Costs the app itself writes open on any heap (R05-4): a 96 MiB heap, as on Android Go,
        // used to refuse every vault and backup made with KdfParams.DEFAULT.
        assertEquals(sampleData, VaultContainer.open(blob, "clave".toCharArray(), maxHeapBytes = 64 * 1024L).data)
        VaultContainer.ensureKdfFitsInMemory(KdfParams.DEFAULT, 96L * 1024 * 1024)
        VaultContainer.ensureKdfFitsInMemory(KdfParams.DEFAULT, 1L)
        VaultContainer.ensureKdfFitsInMemory(testParams, 1L)

        // Above DEFAULT the relative check applies: 65 MiB needs a heap of at least 130 MiB.
        VaultContainer.ensureKdfFitsInMemory(aboveDefault, 130L * 1024 * 1024)
        try {
            VaultContainer.ensureKdfFitsInMemory(aboveDefault, 130L * 1024 * 1024 - 1)
            fail("Costs above the default were accepted for a heap too small for them")
        } catch (expected: KdfMemoryException) {
        }
        // The hard cap holds regardless of the heap.
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
    fun upgradeKdfKeepsTheDekAndRewrapsItUnderTheNewCosts() {
        val created = VaultContainer.create("maestra".toCharArray(), sampleData, testParams)
        val oldBlob = VaultContainer.seal(created.header, created.dek, created.data)
        val stronger = KdfParams(memoryKiB = 128, iterations = 2, parallelism = 1)
        val upgraded = VaultContainer.upgradeKdf("maestra".toCharArray(), created.dek, stronger)
        val blob = VaultContainer.seal(upgraded, created.dek, created.data)

        assertEquals(stronger, upgraded.kdfParams)
        assertFalse("The salt must be fresh", upgraded.salt.contentEquals(created.header.salt))
        // The same password opens it and gives back the very same DEK: the fingerprint copy still works.
        val opened = VaultContainer.open(blob, "maestra".toCharArray())
        assertEquals(sampleData, opened.data)
        assertArrayEquals(created.dek, opened.dek)
        assertEquals(sampleData, VaultContainer.openWithKey(blob, created.dek).data)
        try {
            VaultContainer.open(blob, "maestrA".toCharArray())
            fail("A wrong password opened the upgraded vault")
        } catch (expected: WrongPasswordException) {
        }
        // The body is bound to the header it was sealed under: neither one goes with the other's.
        val oldBody = oldBlob.copyOfRange(created.header.encoded.size, oldBlob.size)
        val newBody = blob.copyOfRange(upgraded.encoded.size, blob.size)
        for (mixed in listOf(upgraded.encoded + oldBody, created.header.encoded + newBody)) {
            try {
                VaultContainer.openWithKey(mixed, created.dek)
                fail("A body was accepted under a header it was not sealed with")
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
