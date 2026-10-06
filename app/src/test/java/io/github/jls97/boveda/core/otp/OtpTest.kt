package io.github.jls97.boveda.core.otp

import io.github.jls97.boveda.core.crypto.AesGcm
import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.vault.ByteWriter
import io.github.jls97.boveda.core.vault.CorruptedVaultException
import io.github.jls97.boveda.core.vault.OtpKeyring
import io.github.jls97.boveda.core.vault.SealedOtp
import io.github.jls97.boveda.core.vault.UnsupportedVaultException
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.core.vault.VaultException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Random

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
        assertNull(Base32.decode("MZ=XW6")) // padding in the middle
        assertNull(Base32.decode("MZXW6=YQ"))
        assertArrayEquals("foo".toByteArray(), Base32.decode("MZXW6==="))
        assertArrayEquals(ByteArray(0), Base32.decode(""))
        assertArrayEquals(ByteArray(0), Base32.decode("  - -"))
        assertArrayEquals(ByteArray(0), Base32.decode("=")) // only padding
        // Leftover bits at the end are dropped, as authenticator apps do: both spell "foo".
        assertArrayEquals(Base32.decode("MZXW6"), Base32.decode("MZXW7"))
        assertArrayEquals("foo".toByteArray(), Base32.decode("MZXW7"))
    }

    @Test
    fun base32AndRecoveryCodesRejectUnicodeLookAlikes() {
        // uppercaseChar() turns the dotless i and the long s into I and S: they are not base32.
        assertNull(Base32.decode("ıbswy3dpehpk3pxp"))
        assertNull(Base32.decode("JB\u017fWY3DPEHPK3PXP"))
        assertNull(Base32.decode("JBSWY3DPEHPK3PXP\u00a0")) // no-break space
        assertNull(Base32.decode("ＪBSWY3DPEHPK3PXP")) // fullwidth J
        assertNull(RecoveryCode.normalize("0ı100ABCDEFGHJKMNPQR"))
        assertNull(RecoveryCode.normalize("01100ABCDEFGHJKMNPQ\u017f"))
        assertNull(RecoveryCode.normalize("01100ABCDEFGHJKMNPQR\u00a0"))
        assertArrayEquals("01100ABCDEFGHJKMNPQR".toCharArray(), RecoveryCode.normalize("01100ABCDEFGHJKMNPQR"))
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
    fun otpauthLinkEdgeCases() {
        fun parse(text: String) = OtpInput.parse(text)
        fun valid(text: String) = (parse(text) as OtpInputResult.Valid).secret
        fun error(text: String) = (parse(text) as OtpInputResult.Invalid).error
        val key = "JBSWY3DPEHPK3PXP"

        // Limits are inclusive: 6..8 digits, 10..300 seconds, 10..128 bytes of key.
        assertEquals(8, valid("otpauth://totp/x?secret=$key&digits=8").params.digits)
        assertEquals(OtpInputError.INVALID_DIGITS, error("otpauth://totp/x?secret=$key&digits=9"))
        assertEquals(OtpInputError.INVALID_DIGITS, error("otpauth://totp/x?secret=$key&digits=5"))
        assertEquals(OtpInputError.INVALID_DIGITS, error("otpauth://totp/x?secret=$key&digits="))
        assertEquals(OtpInputError.INVALID_DIGITS, error("otpauth://totp/x?secret=$key&digits=seis"))
        assertEquals(300, valid("otpauth://totp/x?secret=$key&period=300").params.period)
        assertEquals(10, valid("otpauth://totp/x?secret=$key&period=10").params.period)
        assertEquals(OtpInputError.INVALID_PERIOD, error("otpauth://totp/x?secret=$key&period=301"))
        assertEquals(OtpInputError.INVALID_PERIOD, error("otpauth://totp/x?secret=$key&period=9"))
        assertEquals(OtpInputError.INVALID_PERIOD, error("otpauth://totp/x?secret=$key&period=99999999999"))
        val key128 = Base32.encode(ByteArray(128) { it.toByte() })
        val key129 = Base32.encode(ByteArray(129) { it.toByte() })
        assertEquals(128, valid("otpauth://totp/x?secret=$key128").key.size)
        assertEquals(128, valid(key128).key.size)
        assertEquals(OtpInputError.INVALID_SECRET, error("otpauth://totp/x?secret=$key129"))
        assertEquals(OtpInputError.INVALID_SECRET, error(key129))
        assertEquals(10, valid(Base32.encode(ByteArray(10) { 1 })).key.size)
        assertEquals(OtpInputError.SECRET_TOO_SHORT, error(Base32.encode(ByteArray(9) { 1 })))

        // Duplicate parameters: the first one wins, and names are case-insensitive.
        val other = "GEZDGNBVGY3TQOJQ"
        assertArrayEquals(Base32.decode(key), valid("otpauth://totp/x?secret=$key&secret=$other").key)
        assertArrayEquals(Base32.decode(key), valid("otpauth://totp/x?SECRET=$key&secret=$other").key)
        assertEquals(8, valid("otpauth://totp/x?secret=$key&digits=8&digits=9").params.digits)
        assertEquals(OtpInputError.INVALID_DIGITS, error("otpauth://totp/x?secret=$key&digits=9&digits=8"))
        assertEquals("A", valid("otpauth://totp/x?secret=$key&issuer=A&issuer=B").issuer)
        // Empty pairs and parameters without value are harmless.
        assertEquals("", valid("otpauth://totp/x?&secret=$key&&issuer&=").issuer)
        assertEquals(OtpInputError.MISSING_SECRET, error("otpauth://totp/x?secret"))
        assertEquals(OtpInputError.MISSING_SECRET, error("otpauth://totp/x?secret="))
        assertEquals(OtpInputError.MISSING_SECRET, error("otpauth://totp/x"))

        // Labels: the first ':' separates the issuer; the rest, colons included, is the account.
        val multi = valid("otpauth://totp/a:b:c?secret=$key")
        assertEquals("a", multi.issuer)
        assertEquals("b:c", multi.account)
        val empty = valid("otpauth://totp/?secret=$key")
        assertEquals("", empty.issuer)
        assertEquals("", empty.account)
        val onlyColon = valid("otpauth://totp/:?secret=$key")
        assertEquals("", onlyColon.issuer)
        assertEquals("", onlyColon.account)
        // A label of 10 000 characters is cut to what the screen can show.
        val long = valid("otpauth://totp/" + "i".repeat(10_000) + ":" + "a".repeat(10_000) + "?secret=$key&issuer=" + "x".repeat(10_000))
        assertEquals(OtpSecret.MAX_LABEL_LENGTH, long.issuer.length)
        assertEquals(OtpSecret.MAX_LABEL_LENGTH, long.account.length)
        assertEquals("x".repeat(200), long.issuer)
        // "TOTP" in any case, but nothing else; an extra path segment is part of the label.
        assertEquals("x", valid("otpauth://Totp/x?secret=$key").account)
        assertEquals(OtpInputError.NOT_TIME_BASED, error("otpauth://totpx/x?secret=$key"))
        assertEquals(OtpInputError.NOT_TIME_BASED, error("otpauth:///x?secret=$key"))
        assertEquals("a/b", valid("otpauth://totp/a/b?secret=$key").account)
    }

    @Test
    fun percentDecodingNeverThrowsOnMalformedSequences() {
        fun account(label: String) = (OtpInput.parse("otpauth://totp/$label?secret=JBSWY3DPEHPK3PXP") as OtpInputResult.Valid).secret.account
        assertEquals("a b", account("a%20b"))
        assertEquals("%", account("%25"))
        assertEquals("%%", account("%25%25"))
        assertEquals("25", account("%2525").drop(1)) // "%25" then "25"
        // Truncated or malformed sequences stay literal.
        assertEquals("a%", account("a%"))
        assertEquals("a%4", account("a%4"))
        assertEquals("a%zz", account("a%zz"))
        assertEquals("a%4z", account("a%4z"))
        assertEquals("%", account("%"))
        assertEquals("%%", account("%%"))
        assertEquals("%%4", account("%%4"))
        assertEquals("%41", account("%2541"))
        assertEquals("A%", account("%41%"))
        // Invalid UTF-8 decodes to replacement characters, with no exception.
        val invalid = account("a%ff%feb")
        assertTrue(invalid.startsWith("a") && invalid.endsWith("b"))
        assertTrue(invalid.contains('\uFFFD'))
        assertTrue(account("%c3").contains('\uFFFD')) // a lone lead byte
        assertEquals("é", account("%C3%A9"))
        assertEquals("é", account("%c3%a9"))
        assertEquals("😀", account("%F0%9F%98%80"))
        // Percent sequences in the query are decoded too: a "%3D" in the secret is not base32.
        assertEquals(OtpInputError.INVALID_SECRET, (OtpInput.parse("otpauth://totp/x?secret=JBSWY3DP%2AEHPK3PXP") as OtpInputResult.Invalid).error)
        assertTrue(OtpInput.parse("otpauth://totp/x?secret=JBSWY3DP%20EHPK3PXP") is OtpInputResult.Valid)
        assertTrue(OtpInput.parse("otpauth://totp/x?secr%65t=JBSWY3DPEHPK3PXP") is OtpInputResult.Valid)
    }

    @Test
    fun parsingRandomInputNeverThrows() {
        // What a hostile QR can carry: random mixes of delimiters, hex digits and non-ASCII text.
        val alphabet = "otpauh:/?&=%.-+ :;@#ABCDEFGHJKMNPQRSTVWXYZabcdefxyz0123456789ñ€😀\u0131\u017f\uFFFD\u0000"
        val random = Random(20_241_006L)
        val prefixes = listOf("", "otpauth://totp/", "otpauth://totp/x?secret=", "otpauth://hotp/", "otpauth-migration://", "https://")
        var valid = 0
        repeat(1_000) {
            val length = random.nextInt(65)
            val body = CharArray(length) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
            val text = prefixes[random.nextInt(prefixes.size)] + body
            when (OtpInput.parse(text)) {
                is OtpInputResult.Valid -> valid++
                is OtpInputResult.Invalid -> Unit
            }
            RecoveryCode.normalize(text)?.let { assertEquals(RecoveryCode.LENGTH, it.size) }
            Base32.decode(text)?.let { assertTrue(it.size <= text.length * 5 / 8) }
        }
        assertTrue(valid < 1_000)
    }

    @Test
    fun algorithmIdsAreStable() {
        // These ids live inside every sealed 2FA secret: they never change, whatever the enum order.
        assertEquals(1, OtpAlgorithm.SHA1.id)
        assertEquals(2, OtpAlgorithm.SHA256.id)
        assertEquals(3, OtpAlgorithm.SHA512.id)
        assertEquals(OtpAlgorithm.entries.size, OtpAlgorithm.entries.map { it.id }.toSet().size)
        for (algorithm in OtpAlgorithm.entries) assertEquals(algorithm, OtpAlgorithm.fromId(algorithm.id))
        assertNull(OtpAlgorithm.fromId(0))
        assertNull(OtpAlgorithm.fromId(4))
        assertNull(OtpAlgorithm.fromId(-1))
        assertEquals(OtpAlgorithm.SHA256, OtpAlgorithm.fromName("sha-256"))
        assertNull(OtpAlgorithm.fromName("2"))

        // The wire value is the id, not the ordinal: a record written with 2 is SHA-256.
        val otpKey = OtpCrypto.newKey()
        val keyringId = OtpCrypto.newKeyringId()
        for ((id, algorithm) in listOf(1 to OtpAlgorithm.SHA1, 2 to OtpAlgorithm.SHA256, 3 to OtpAlgorithm.SHA512)) {
            val sealed = sealRecord(otpKey, keyringId, "e", record(fieldKey to rfcKeySha1, fieldAlgorithm to i32(id)))
            assertEquals(algorithm, OtpCrypto.open(otpKey, keyringId, "e", sealed).params.algorithm)
            val written = OtpCrypto.seal(otpKey, keyringId, "e", OtpSecret(rfcKeySha1, OtpParams(algorithm)))
            assertEquals(id, algorithmFieldOf(otpKey, keyringId, "e", written))
        }
    }

    // --- Hand-made 2FA records, sealed with the real AAD, to exercise every rejection of OtpCrypto.open.

    private val fieldKey = 1
    private val fieldAlgorithm = 2
    private val fieldDigits = 3
    private val fieldPeriod = 4
    private val fieldIssuer = 5
    private val fieldAccount = 6

    private fun i32(value: Int): ByteArray = ByteWriter(4).apply { putI32(value) }.toByteArray()

    /** `version u16, fieldCount u16, (tag u16, length u32, value)*`; the defaults make a valid record. */
    private fun record(vararg fields: Pair<Int, ByteArray>, version: Int = 1, trailing: ByteArray = ByteArray(0)): ByteArray {
        val defaults = linkedMapOf(fieldAlgorithm to i32(1), fieldDigits to i32(6), fieldPeriod to i32(30))
        val all = ArrayList<Pair<Int, ByteArray>>()
        for ((tag, value) in fields) {
            defaults.remove(tag)
            all += tag to value
        }
        for ((tag, value) in defaults) all += tag to value
        val writer = ByteWriter(256)
        writer.putU16(version)
        writer.putU16(all.size)
        for ((tag, value) in all) {
            writer.putU16(tag)
            writer.putI32(value.size)
            writer.putBytes(value)
        }
        writer.putBytes(trailing)
        return writer.toByteArray()
    }

    private fun sealRecord(otpKey: ByteArray, keyringId: ByteArray, entryId: String, plaintext: ByteArray): SealedOtp =
        SealedOtp(AesGcm.seal(otpKey, plaintext, OtpCrypto.secretAad(keyringId, entryId)))

    /** The algorithm field as written on the wire by [OtpCrypto.seal]. */
    private fun algorithmFieldOf(otpKey: ByteArray, keyringId: ByteArray, entryId: String, sealed: SealedOtp): Int {
        val plaintext = AesGcm.open(otpKey, sealed.bytes, OtpCrypto.secretAad(keyringId, entryId))
        var offset = 4 // version and field count
        repeat((plaintext[2].toInt() shl 8) or plaintext[3].toInt()) {
            val tag = ((plaintext[offset].toInt() and 0xFF) shl 8) or (plaintext[offset + 1].toInt() and 0xFF)
            val length = ByteBuffer.wrap(plaintext, offset + 2, 4).int
            if (tag == fieldAlgorithm) return ByteBuffer.wrap(plaintext, offset + 6, 4).int
            offset += 6 + length
        }
        fail("No algorithm field in the sealed record")
        return 0
    }

    private inline fun <reified T : VaultException> expect(what: String, block: () -> Unit) {
        try {
            block()
            fail("$what was accepted")
        } catch (e: VaultException) {
            if (e !is T) fail("$what: expected ${T::class.simpleName}, got ${e::class.simpleName}")
        }
    }

    @Test
    fun openRejectsMalformedRecords() {
        val otpKey = OtpCrypto.newKey()
        val keyringId = OtpCrypto.newKeyringId()
        fun open(plaintext: ByteArray) = OtpCrypto.open(otpKey, keyringId, "e1", sealRecord(otpKey, keyringId, "e1", plaintext))

        // Sanity: the builder's defaults round-trip, with labels or without them.
        val plain = open(record(fieldKey to rfcKeySha1))
        assertEquals(OtpSecret(rfcKeySha1, OtpParams()), plain)
        val labelled = open(record(fieldKey to rfcKeySha1, fieldIssuer to "ACME".toByteArray(), fieldAccount to "yo".toByteArray()))
        assertEquals("ACME · yo", labelled.label)
        assertEquals(128, open(record(fieldKey to ByteArray(128) { 9 })).key.size)
        assertEquals(1, open(record(fieldKey to ByteArray(1) { 9 })).key.size)
        assertEquals(OtpParams(OtpAlgorithm.SHA512, 8, 300), open(record(fieldKey to rfcKeySha1, fieldAlgorithm to i32(3), fieldDigits to i32(8), fieldPeriod to i32(300))).params)
        // Unknown tags are skipped, as in the rest of the format.
        assertEquals(plain, open(record(fieldKey to rfcKeySha1, 99 to "future".toByteArray())))

        expect<UnsupportedVaultException>("a 2FA record of version 2") { open(record(fieldKey to rfcKeySha1, version = 2)) }
        expect<UnsupportedVaultException>("a 2FA record of version 0") { open(record(fieldKey to rfcKeySha1, version = 0)) }
        expect<UnsupportedVaultException>("algorithm 4") { open(record(fieldKey to rfcKeySha1, fieldAlgorithm to i32(4))) }
        expect<UnsupportedVaultException>("algorithm 0") { open(record(fieldKey to rfcKeySha1, fieldAlgorithm to i32(0))) }
        expect<CorruptedVaultException>("an algorithm field of 2 bytes") { open(record(fieldKey to rfcKeySha1, fieldAlgorithm to byteArrayOf(0, 1))) }
        expect<CorruptedVaultException>("5 digits") { open(record(fieldKey to rfcKeySha1, fieldDigits to i32(5))) }
        expect<CorruptedVaultException>("9 digits") { open(record(fieldKey to rfcKeySha1, fieldDigits to i32(9))) }
        expect<CorruptedVaultException>("a period of 9 seconds") { open(record(fieldKey to rfcKeySha1, fieldPeriod to i32(9))) }
        expect<CorruptedVaultException>("a period of 301 seconds") { open(record(fieldKey to rfcKeySha1, fieldPeriod to i32(301))) }
        expect<CorruptedVaultException>("an empty key") { open(record(fieldKey to ByteArray(0))) }
        expect<CorruptedVaultException>("a key of 129 bytes") { open(record(fieldKey to ByteArray(129) { 9 })) }
        expect<CorruptedVaultException>("a record without key") { open(record()) }
        expect<CorruptedVaultException>("trailing data") { open(record(fieldKey to rfcKeySha1, trailing = byteArrayOf(0))) }
        expect<CorruptedVaultException>("an empty record") { open(ByteArray(0)) }
        expect<CorruptedVaultException>("a record cut inside a field") { open(record(fieldKey to rfcKeySha1).copyOf(12)) }
        // A key given twice: the last one wins, and the first copy is wiped.
        val twice = open(record(fieldKey to rfcKeySha1, fieldKey to rfcKeySha256, fieldAlgorithm to i32(2)))
        assertArrayEquals(rfcKeySha256, twice.key)
    }

    @Test
    fun keyringBindsTheWrappedKeyToEveryFieldOfTheHeader() {
        val otpKey = OtpCrypto.newKey()
        val code = RecoveryCode.generate()
        val keyring = OtpCrypto.createKeyring(otpKey, OtpCrypto.newKeyringId(), code, testParams)
        assertArrayEquals(otpKey, OtpCrypto.unwrapWithRecoveryCode(keyring, code))

        fun flipped(bytes: ByteArray) = bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val altered = mapOf(
            "the keyring id" to OtpKeyring(flipped(keyring.id), keyring.kdfParams, keyring.salt, keyring.wrappedKey),
            "the salt" to OtpKeyring(keyring.id, keyring.kdfParams, flipped(keyring.salt), keyring.wrappedKey),
            "the memory" to OtpKeyring(keyring.id, keyring.kdfParams.copy(memoryKiB = 72), keyring.salt, keyring.wrappedKey),
            "the iterations" to OtpKeyring(keyring.id, keyring.kdfParams.copy(iterations = 2), keyring.salt, keyring.wrappedKey),
            "the lanes" to OtpKeyring(keyring.id, keyring.kdfParams.copy(parallelism = 2), keyring.salt, keyring.wrappedKey),
            "the wrapped key" to OtpKeyring(keyring.id, keyring.kdfParams, keyring.salt, flipped(keyring.wrappedKey)),
        )
        for ((what, broken) in altered) {
            assertNotEquals(keyring, broken)
            try {
                OtpCrypto.unwrapWithRecoveryCode(broken, code)
                fail("A keyring with $what altered still unwrapped the 2FA key")
            } catch (expected: WrongRecoveryCodeException) {
            }
        }
        // The untouched keyring still works: the failures above were the alterations, not the code.
        assertArrayEquals(otpKey, OtpCrypto.unwrapWithRecoveryCode(keyring, code))
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
