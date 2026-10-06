package io.github.jls97.boveda.core.otp

import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.vault.CorruptedVaultException
import io.github.jls97.boveda.core.vault.VaultEntry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OtpTest {

    // Cheap Argon2 costs keep the tests fast; the app always uses KdfParams.DEFAULT.
    private val testParams = KdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)

    private val rfcKeySha1 = "12345678901234567890".toByteArray()
    private val rfcKeySha256 = "12345678901234567890123456789012".toByteArray()
    private val rfcKeySha512 = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    @Test
    fun totpMatchesRfc6238TestVectors() {
        // RFC 6238, appendix B: 8 digits, 30-second period.
        val vectors = listOf(
            59L to listOf("94287082", "46119246", "90693936"),
            1_111_111_109L to listOf("07081804", "68084774", "25091201"),
            1_111_111_111L to listOf("14050471", "67062674", "99943326"),
            1_234_567_890L to listOf("89005924", "91819424", "93441116"),
            2_000_000_000L to listOf("69279037", "90698825", "38618901"),
            20_000_000_000L to listOf("65353130", "77737706", "47863826"),
        )
        for ((seconds, expected) in vectors) {
            val millis = seconds * 1_000
            assertEquals(expected[0], Totp.code(rfcKeySha1, OtpParams(OtpAlgorithm.SHA1, 8), millis))
            assertEquals(expected[1], Totp.code(rfcKeySha256, OtpParams(OtpAlgorithm.SHA256, 8), millis))
            assertEquals(expected[2], Totp.code(rfcKeySha512, OtpParams(OtpAlgorithm.SHA512, 8), millis))
        }
    }

    @Test
    fun hotpMatchesRfc4226TestVectors() {
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { counter, code ->
            assertEquals(code, Totp.hotp(rfcKeySha1, counter.toLong(), OtpAlgorithm.SHA1, 6))
        }
    }

    @Test
    fun periodsAndCountdown() {
        val params = OtpParams()
        assertEquals(0L, Totp.counter(params, 29_999))
        assertEquals(1L, Totp.counter(params, 30_000))
        assertEquals(30, Totp.secondsLeft(params, 30_000))
        assertEquals(1, Totp.secondsLeft(params, 59_999))
        assertEquals(60, Totp.secondsLeft(OtpParams(period = 60), 0))
        // The same code for the whole period, a new one right after.
        val key = rfcKeySha1
        assertEquals(Totp.code(key, params, 30_000), Totp.code(key, params, 59_999))
        assertNotEquals(Totp.code(key, params, 59_999), Totp.code(key, params, 60_000))
    }

    @Test
    fun codesAreGroupedForReading() {
        assertEquals("123 456", Totp.format("123456"))
        assertEquals("123 4567", Totp.format("1234567"))
        assertEquals("1234 5678", Totp.format("12345678"))
    }

    @Test
    fun base32FollowsRfc4648() {
        val vectors = listOf("" to "", "f" to "MY", "fo" to "MZXQ", "foo" to "MZXW6", "foob" to "MZXW6YQ", "fooba" to "MZXW6YTB", "foobar" to "MZXW6YTBOI")
        for ((plain, encoded) in vectors) {
            assertEquals(encoded, Base32.encode(plain.toByteArray()))
            assertArrayEquals(plain.toByteArray(), Base32.decode(encoded))
        }
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
    }

    @Test
    fun base32DecodingIsLenientLikeAuthenticatorApps() {
        val key = Base32.decode("JBSWY3DPEHPK3PXP")!!
        assertArrayEquals(key, Base32.decode("jbsw y3dp ehpk 3pxp"))
        assertArrayEquals(key, Base32.decode("JBSW-Y3DP-EHPK-3PXP"))
        assertNull(Base32.decode("JBSWY3DPEHPK3PX1")) // 1 is not base32
        assertNull(Base32.decode("MY======MY")) // data after the padding
    }

    @Test
    fun parsesAFullOtpauthLink() {
        val result = OtpInput.parse(
            "otpauth://totp/ACME%20Co:john.doe+tag@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ" +
                "&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60",
        )
        val secret = (result as OtpInputResult.Valid).secret
        assertEquals("ACME Co", secret.issuer)
        assertEquals("john.doe+tag@email.com", secret.account)
        assertEquals(OtpParams(OtpAlgorithm.SHA256, 8, 60), secret.params)
        assertArrayEquals(Base32.decode("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ"), secret.key)
        assertEquals("ACME Co · john.doe+tag@email.com", secret.label)
    }

    @Test
    fun otpauthLinkDefaultsAndLabels() {
        val plain = OtpInput.parse("otpauth://totp/alice@google.com?secret=JBSWY3DPEHPK3PXP") as OtpInputResult.Valid
        assertEquals(OtpParams.DEFAULT, plain.secret.params)
        assertEquals("", plain.secret.issuer)
        assertEquals("alice@google.com", plain.secret.account)

        val fromLabel = OtpInput.parse("OTPAUTH://TOTP/Example%3A%20alice?SECRET=jbswy3dpehpk3pxp") as OtpInputResult.Valid
        assertEquals("Example", fromLabel.secret.issuer)
        assertEquals("alice", fromLabel.secret.account)

        val issuerWins = OtpInput.parse("otpauth://totp/Old:bob?secret=JBSWY3DPEHPK3PXP&issuer=New") as OtpInputResult.Valid
        assertEquals("New", issuerWins.secret.issuer)
        assertEquals("bob", issuerWins.secret.account)

        val emoji = OtpInput.parse("otpauth://totp/Caf%C3%A9 😀:me?secret=JBSWY3DPEHPK3PXP") as OtpInputResult.Valid
        assertEquals("Café 😀", emoji.secret.issuer)
    }

    @Test
    fun rejectsWhatIsNotAUsableTotpSecret() {
        fun error(text: String) = (OtpInput.parse(text) as OtpInputResult.Invalid).error
        assertEquals(OtpInputError.EMPTY, error("  "))
        assertEquals(OtpInputError.NOT_TIME_BASED, error("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1"))
        assertEquals(OtpInputError.MIGRATION_EXPORT, error("otpauth-migration://offline?data=abc"))
        assertEquals(OtpInputError.NOT_OTPAUTH, error("https://example.com/2fa"))
        assertEquals(OtpInputError.MISSING_SECRET, error("otpauth://totp/x?issuer=y"))
        assertEquals(OtpInputError.INVALID_SECRET, error("otpauth://totp/x?secret=NOT*BASE32"))
        assertEquals(OtpInputError.SECRET_TOO_SHORT, error("JBSWY3DP"))
        assertEquals(OtpInputError.UNSUPPORTED_ALGORITHM, error("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&algorithm=MD5"))
        assertEquals(OtpInputError.INVALID_DIGITS, error("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=4"))
        assertEquals(OtpInputError.INVALID_PERIOD, error("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&period=0"))
    }

    @Test
    fun bareSecretUsesTheChosenParameters() {
        val params = OtpParams(OtpAlgorithm.SHA512, 7, 45)
        val result = OtpInput.parse(" jbsw y3dp ehpk 3pxp ", params) as OtpInputResult.Valid
        assertEquals(params, result.secret.params)
        assertEquals("", result.secret.label)
        assertEquals(Totp.code(Base32.decode("JBSWY3DPEHPK3PXP")!!, params, 1_000_000), result.secret.code(1_000_000))
    }

    @Test
    fun sealedSecretRoundTripsAndStaysBoundToItsEntry() {
        val otpKey = OtpCrypto.newKey()
        val keyringId = OtpCrypto.newKeyringId()
        val secret = OtpSecret(rfcKeySha1, OtpParams(OtpAlgorithm.SHA256, 8, 60), "ACME", "yo@example.com")
        val sealed = OtpCrypto.seal(otpKey, keyringId, "entry-1", secret)
        assertEquals(secret, OtpCrypto.open(otpKey, keyringId, "entry-1", sealed))

        expectCorrupted { OtpCrypto.open(otpKey, keyringId, "entry-2", sealed) }
        expectCorrupted { OtpCrypto.open(otpKey, OtpCrypto.newKeyringId(), "entry-1", sealed) }
        expectCorrupted { OtpCrypto.open(OtpCrypto.newKey(), keyringId, "entry-1", sealed) }
        val tampered = sealed.bytes.also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        expectCorrupted { OtpCrypto.open(otpKey, keyringId, "entry-1", io.github.jls97.boveda.core.vault.SealedOtp(tampered)) }
    }

    @Test
    fun recoveryCodeUnwrapsTheKeyAndNothingElseDoes() {
        val otpKey = OtpCrypto.newKey()
        val code = RecoveryCode.generate()
        val keyring = OtpCrypto.createKeyring(otpKey, OtpCrypto.newKeyringId(), code, testParams)
        assertArrayEquals(otpKey, OtpCrypto.unwrapWithRecoveryCode(keyring, code))

        val typed = RecoveryCode.normalize(RecoveryCode.format(code).lowercase().replace("-", " "))!!
        assertArrayEquals(otpKey, OtpCrypto.unwrapWithRecoveryCode(keyring, typed))

        val other = RecoveryCode.generate()
        try {
            OtpCrypto.unwrapWithRecoveryCode(keyring, other)
            fail("A different recovery code unwrapped the 2FA key")
        } catch (expected: WrongRecoveryCodeException) {
        }
    }

    @Test
    fun resealMovesEverySecretToTheNewKeyAndNothingOpensWithTheOld() {
        val oldKey = OtpCrypto.newKey()
        val oldId = OtpCrypto.newKeyringId()
        val first = OtpSecret(rfcKeySha1, OtpParams(), "ACME", "yo@example.com")
        val second = OtpSecret(rfcKeySha256, OtpParams(OtpAlgorithm.SHA256, 8, 60), "Banco", "")
        val entries = listOf(
            VaultEntry(id = "e1", title = "ACME", createdAt = 1, updatedAt = 2, otp = OtpCrypto.seal(oldKey, oldId, "e1", first)),
            VaultEntry(id = "e2", title = "Sin 2FA", createdAt = 3, updatedAt = 4),
            VaultEntry(id = "e3", title = "Banco", createdAt = 5, updatedAt = 6, otp = OtpCrypto.seal(oldKey, oldId, "e3", second)),
        )

        val newKey = OtpCrypto.newKey()
        val newId = OtpCrypto.newKeyringId()
        val resealed = OtpCrypto.reseal(entries, oldKey, oldId, newKey, newId)

        assertEquals(entries.map { it.id }, resealed.map { it.id })
        assertEquals(entries[1], resealed[1])
        assertNull(resealed[1].otp)
        // Only the sealed secret changes: the entry itself is untouched.
        assertEquals(entries[0].copy(otp = null), resealed[0].copy(otp = null))
        assertEquals(first, OtpCrypto.open(newKey, newId, "e1", resealed[0].otp!!))
        assertEquals(second, OtpCrypto.open(newKey, newId, "e3", resealed[2].otp!!))
        assertNotEquals(entries[0].otp, resealed[0].otp)
        expectCorrupted { OtpCrypto.open(oldKey, oldId, "e1", resealed[0].otp!!) }
        expectCorrupted { OtpCrypto.open(oldKey, newId, "e3", resealed[2].otp!!) }
        expectCorrupted { OtpCrypto.open(newKey, oldId, "e3", resealed[2].otp!!) }
        // The old copies keep opening only with the old key: the one found in an old backup.
        assertEquals(first, OtpCrypto.open(oldKey, oldId, "e1", entries[0].otp!!))
        expectCorrupted { OtpCrypto.open(newKey, newId, "e1", entries[0].otp!!) }
    }

    @Test
    fun resealRefusesAWrongOldKey() {
        val oldKey = OtpCrypto.newKey()
        val oldId = OtpCrypto.newKeyringId()
        val secret = OtpSecret(rfcKeySha1, OtpParams(), "ACME", "yo@example.com")
        val entries = listOf(VaultEntry(id = "e1", title = "ACME", createdAt = 1, updatedAt = 2, otp = OtpCrypto.seal(oldKey, oldId, "e1", secret)))
        expectCorrupted { OtpCrypto.reseal(entries, OtpCrypto.newKey(), oldId, OtpCrypto.newKey(), OtpCrypto.newKeyringId()) }
    }

    @Test
    fun recoveryCodesHaveAReadableFormat() {
        val code = RecoveryCode.generate()
        assertEquals(RecoveryCode.LENGTH, code.size)
        val formatted = RecoveryCode.format(code)
        assertTrue(formatted.matches(Regex("[0-9A-HJKMNP-TV-Z]{5}(-[0-9A-HJKMNP-TV-Z]{5}){3}")))
        assertArrayEquals(code, RecoveryCode.normalize(formatted))
        // Letters that look like digits are read as digits.
        assertArrayEquals("01100ABCDEFGHJKMNPQR".toCharArray(), RecoveryCode.normalize("oIL0o-abcde-fghjk-mnpqr"))
        assertNull(RecoveryCode.normalize("0110-0ABCD")) // too short
        assertNull(RecoveryCode.normalize("01100ABCDEFGHJKMNPQRS")) // too long
        assertNull(RecoveryCode.normalize("0110UABCDEFGHJKMNPQR")) // U is not used
    }

    private fun expectCorrupted(block: () -> Unit) {
        try {
            block()
            fail("A sealed 2FA secret opened where it shouldn't")
        } catch (expected: CorruptedVaultException) {
        }
    }
}
