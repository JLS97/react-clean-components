package io.github.jls97.boveda.core.vault

import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.crypto.wipe
import io.github.jls97.boveda.core.otp.OtpAlgorithm
import io.github.jls97.boveda.core.otp.OtpCrypto
import io.github.jls97.boveda.core.otp.OtpParams
import io.github.jls97.boveda.core.otp.OtpSecret
import io.github.jls97.boveda.core.otp.RecoveryCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * Fixtures binarios inmutables del formato de bóveda (I-18, R07-1, R07-3).
 *
 * Hay dos juegos, los dos de la versión 1 del formato (cuerpo y contenedor), y cada uno lo escribió
 * una versión real del codificador:
 * - `vault-v1-legacy-plain.bin` y `vault-v1-legacy.bvd` los generó el código de la app publicada
 *   antes de la auditoría (commit 05b7c78): el registro de ajustes lleva solo los dos ajustes y el
 *   llavero 2FA, sin el campo «reader version» (tag 4), y los secretos 2FA guardan el algoritmo
 *   como `ordinal + 1`. Es el formato que tienen las copias que los usuarios ya guardaron.
 * - `vault-v1-plain.bin` y `vault-v1.bvd` los generó el codificador con el campo «reader version»
 *   (fase 4 de las correcciones): mismo cuerpo, con el tag 4 = 1 delante de los ajustes.
 *
 * Los cuatro se generaron UNA sola vez y NO se regeneran nunca: si un cambio del codificador o de
 * la cabecera rompe estos tests, la app ha dejado de abrir las copias que los usuarios ya
 * guardaron, y lo que hay que arreglar es el lector (compatibilidad hacia atrás), no los archivos.
 * Cada versión nueva del formato añade un fixture nuevo (`vault-v2-plain.bin`, `vault-v2.bvd`...) y
 * conserva los anteriores.
 *
 * El [VaultData] esperado se construye aquí, campo a campo, sin derivarlo de los archivos. Los
 * únicos valores copiados de la generación son los que salieron de un generador aleatorio en su
 * momento (sal y clave envuelta del llavero 2FA, secretos 2FA sellados): se comprueban byte a byte
 * y además se descifran con la otpKey recuperada del código de recuperación, que sí es conocido.
 *
 * Contraseña maestra de los `.bvd`: `fixture-password`. Código de recuperación 2FA:
 * `B0VEDA2FAREC0VERYC0D`. Parámetros Argon2id mínimos (64 KiB, 1 pasada, 1 carril) para que el
 * test sea rápido; la app usa siempre [KdfParams.DEFAULT].
 */
class VaultFixturesTest {

    private val fixturePassword = "fixture-password"
    private val fixtureRecoveryCode = "B0VEDA2FAREC0VERYC0D"
    private val fixtureKdfParams = KdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)

    /** Identificador del llavero elegido a mano para el fixture: 16 bytes deterministas. */
    private val keyringId = ByteArray(OtpKeyring.ID_SIZE) { (it * 17 + 3).toByte() }

    // Valores aleatorios en el momento de generar los fixtures, copiados de build/vault-fixtures/constants.txt.
    private val keyringSalt = hex("4f8e9cf258a4f8358cd757a379a198b37cd150fcb7c873f3a5426eeb938216ae")
    private val keyringWrappedKey = hex("02bb1df36c03ac71655359795210bba67d0926b46e1275ec6b92232187732195d8f824bcbc0e13237497ea4e2700d74e1be4249619b5e01b62ea9f90")
    private val sealedOtpA1 = hex("82fbbbb56727816d6033c497bc57c76fc5d300876f7fa061dccd422bb08afbe66c72af2c5191a800eab9e032a8f44a229862c6f353c6e5a6b81d6c9a8df111effee6e2c513e6cc38b505da23a4bd437ac3252c478355bd3cde2984d4b09cd33fadcd7af059f9068df832bf8e96bb0d9b2ffdb14996b0")
    private val sealedOtpB2 = hex("d3b06d8c17b5bcc2deddeec40cec9c9e30f0e62b772cba3364eb809e8018eb1080e029c02b365e309bda4f1f9b4960ed1021a2efb97320c74caacce0ad61d9cec35b1515e6bb802f063fe50a3cec7014285b9fdaa85481cdb33409f828675a2d2e14ac8e5bbbc710f3fbf98c15dadc61fe42d4fcbde4")

    // Valores aleatorios al generar los fixtures legacy con el código de 05b7c78 (constants.txt de aquella ejecución).
    private val legacyKeyringSalt = hex("80e7d74df8f7302e4e4ecac7c9bfb659970b215051314486beba4c60fc1cf22f")
    private val legacyKeyringWrappedKey = hex("8503582bd6fdd362bcf6f3331abc604b06fd86879e9799cc4f708c70cd0ec3497a7a52a33023f475bc15b5be0f3b30658325af35e4653aea462c9f6f")
    private val legacySealedOtpA1 = hex("53e42ca9692f753f3bbff48b2f0c55aa2b502dd423d115584fce34ecfc450999b5f1ae9358a6c33e7d5b837d14e2ec78f162907c19d46e4aa11edfcef3a3c22fe021b8daf8e8247f7605980e0a55f0f7569fe1f09b1e56ce4016b408e990630addf4dcf6cde70c7fe8b11f666e07c994c6d8f20aa70f")
    private val legacySealedOtpB2 = hex("195243a951483e2b418edf41789c7b1a7e27c6476fc876f1a507fd0be6f5c6b5f11081e145cfd9e2ba8fc1f2a0bd22c899ce1d5d3a7f639b7682934ca406a5e4fc9ea08d83986de7f040f1120ca7d1f0865dec99f9dcbaa443b94df46c1da9a5be4f5b82ff704f71e054f705584d7ae427c60f934826")

    /** Secretos 2FA en claro con los que se sellaron los campos `otp` (clave SHA-1 del RFC 6238 y otra de 32 bytes). */
    private val otpSecretA1 = OtpSecret("12345678901234567890".toByteArray(), OtpParams(), "ACME", "yo@example.com")
    private val otpSecretB2 = OtpSecret(
        "12345678901234567890123456789012".toByteArray(),
        OtpParams(algorithm = OtpAlgorithm.SHA256, digits = 8, period = 60),
        "Correo",
        "",
    )

    private val expectedEntries = listOf(
        VaultEntry(
            id = "a1",
            title = "Banco",
            username = "yo@example.com",
            password = "p@ss\"word\\ñ€😀",
            url = "https://banco.example",
            notes = "Línea 1\nLínea 2",
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_100_000,
            autofillTargets = listOf("android:com.bank.app", "web:banco.example"),
            otp = SealedOtp(sealedOtpA1),
        ),
        VaultEntry(
            id = "b2",
            title = "Correo",
            username = "usuario",
            password = "otra clave",
            createdAt = 1_600_000_000_000,
            updatedAt = 1_600_000_000_001,
            autofillTargets = listOf("web:correo.example"),
            otp = SealedOtp(sealedOtpB2),
        ),
        VaultEntry(id = "c3", title = "", createdAt = 0, updatedAt = Long.MAX_VALUE),
    )

    private val expectedSettings = VaultSettings(autoLockSeconds = 300, clipboardClearSeconds = 15)

    private val expectedKeyring = OtpKeyring(keyringId, fixtureKdfParams, keyringSalt, keyringWrappedKey)

    private val expectedData = VaultData(settings = expectedSettings, entries = expectedEntries, otpKeyring = expectedKeyring)

    /** Mismo contenido que [expectedData]; solo cambian los bytes que salieron del azar al generar el juego legacy. */
    private val legacyExpectedData = VaultData(
        settings = expectedSettings,
        entries = expectedEntries.map { entry ->
            when (entry.id) {
                "a1" -> entry.copy(otp = SealedOtp(legacySealedOtpA1))
                "b2" -> entry.copy(otp = SealedOtp(legacySealedOtpB2))
                else -> entry
            }
        },
        otpKeyring = OtpKeyring(keyringId, fixtureKdfParams, legacyKeyringSalt, legacyKeyringWrappedKey),
    )

    @Test
    fun plainFixtureDecodesToTheExpectedVaultData() {
        val decoded = VaultCodec.decode(fixture("vault-v1-plain.bin"))
        assertSameVault(expectedData, decoded)
    }

    @Test
    fun bvdFixtureOpensWithTheKnownPasswordAndKeepsTheOtpKeyring() {
        val opened = VaultContainer.open(fixture("vault-v1.bvd"), fixturePassword.toCharArray())
        assertEquals(fixtureKdfParams, opened.header.kdfParams)
        assertSameVault(expectedData, opened.data)

        // El llavero 2FA de la copia sigue abriendo con el código de recuperación y descifra los secretos.
        val keyring = checkNotNull(opened.data.otpKeyring)
        val otpKey = OtpCrypto.unwrapWithRecoveryCode(keyring, checkNotNull(RecoveryCode.normalize(fixtureRecoveryCode)))
        try {
            val a1 = OtpCrypto.open(otpKey, keyring.id, "a1", checkNotNull(opened.data.entries[0].otp))
            val b2 = OtpCrypto.open(otpKey, keyring.id, "b2", checkNotNull(opened.data.entries[1].otp))
            try {
                assertEquals(otpSecretA1, a1)
                assertEquals(otpSecretB2, b2)
                // RFC 6238, apéndice B: en t = 59 s el código de 8 dígitos es 94287082.
                assertEquals("287082", a1.code(59_000))
            } finally {
                a1.wipe()
                b2.wipe()
            }
        } finally {
            otpKey.wipe()
            opened.dek.wipe()
        }
    }

    @Test
    fun legacyPlainFixtureHasNoReaderVersionFieldAndDecodesToTheExpectedVaultData() {
        val payload = fixture("vault-v1-legacy-plain.bin")
        // Versión 1 del cuerpo y un registro de ajustes de TRES campos (auto-bloqueo, portapapeles,
        // llavero 2FA) que empieza por el tag 1: el tag 4 (reader version) no existía todavía.
        assertArrayEquals(byteArrayOf(0, 1, 0, 3, 0, 1), payload.copyOfRange(0, 6))
        // El juego actual, en cambio, lleva cuatro campos y el tag 4 delante.
        assertArrayEquals(byteArrayOf(0, 1, 0, 4, 0, 4), fixture("vault-v1-plain.bin").copyOfRange(0, 6))
        assertSameVault(legacyExpectedData, VaultCodec.decode(payload))
    }

    @Test
    fun legacyBvdFixtureOpensWithTheKnownPasswordAndItsOtpSecretsStillDecrypt() {
        val opened = VaultContainer.open(fixture("vault-v1-legacy.bvd"), fixturePassword.toCharArray())
        assertEquals(fixtureKdfParams, opened.header.kdfParams)
        assertSameVault(legacyExpectedData, opened.data)

        // Los secretos 2FA sellados por el código antiguo (algoritmo como ordinal + 1) se abren
        // con el lector actual (algoritmo por id): SHA-1 y SHA-256 siguen siendo 1 y 2.
        val keyring = checkNotNull(opened.data.otpKeyring)
        val otpKey = OtpCrypto.unwrapWithRecoveryCode(keyring, checkNotNull(RecoveryCode.normalize(fixtureRecoveryCode)))
        try {
            val a1 = OtpCrypto.open(otpKey, keyring.id, "a1", checkNotNull(opened.data.entries[0].otp))
            val b2 = OtpCrypto.open(otpKey, keyring.id, "b2", checkNotNull(opened.data.entries[1].otp))
            try {
                assertEquals(otpSecretA1, a1)
                assertEquals(otpSecretB2, b2)
                assertEquals(OtpAlgorithm.SHA256, b2.params.algorithm)
                assertEquals("287082", a1.code(59_000))
            } finally {
                a1.wipe()
                b2.wipe()
            }
        } finally {
            otpKey.wipe()
            opened.dek.wipe()
        }
    }

    @Test
    fun fixturesStaySmall() {
        val total = listOf("vault-v1-plain.bin", "vault-v1.bvd", "vault-v1-legacy-plain.bin", "vault-v1-legacy.bvd").sumOf { fixture(it).size }
        assertTrue("Los fixtures ocupan $total bytes", total < 64 * 1024)
    }

    /** Compara campo a campo, para que un fallo diga qué campo cambió y no solo que el vault difiere. */
    private fun assertSameVault(expected: VaultData, actual: VaultData) {
        assertEquals(expected.settings, actual.settings)
        assertEquals(expected.entries.size, actual.entries.size)
        for ((want, got) in expected.entries.zip(actual.entries)) {
            assertEquals(want.id, got.id)
            assertEquals(want.title, got.title)
            assertEquals(want.username, got.username)
            assertEquals(want.password, got.password)
            assertEquals(want.url, got.url)
            assertEquals(want.notes, got.notes)
            assertEquals(want.createdAt, got.createdAt)
            assertEquals(want.updatedAt, got.updatedAt)
            assertEquals(want.autofillTargets, got.autofillTargets)
            assertEquals(want.otp, got.otp)
        }
        val keyring = checkNotNull(actual.otpKeyring)
        assertArrayEquals(expected.otpKeyring!!.id, keyring.id)
        assertEquals(expected.otpKeyring.kdfParams, keyring.kdfParams)
        assertArrayEquals(expected.otpKeyring.salt, keyring.salt)
        assertArrayEquals(expected.otpKeyring.wrappedKey, keyring.wrappedKey)
        assertEquals(expected, actual)
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/vault/$name")) { "Falta el fixture $name" }.use { it.readBytes() }

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /**
     * Generador que produjo los fixtures `vault-v1.*` (codificador con «reader version»). Se ejecutó
     * una vez y queda desactivado: NO se vuelve a ejecutar para la versión 1. Cuando el formato
     * cambie de versión, copiar este método para la versión nueva (otros nombres de archivo),
     * quitarle `@Ignore` solo en local, ejecutarlo con
     * `./gradlew :app:testDebugUnitTest --tests '*VaultFixturesTest.generate*'`, pegar en los tests
     * nuevos las constantes que deja en `build/vault-fixtures/constants.txt`, volver a ponerle
     * `@Ignore` y comprometer los binarios en git. Se niega a sobrescribir.
     *
     * Los fixtures `vault-v1-legacy.*` se produjeron con este mismo método copiado a un árbol del
     * commit 05b7c78 (`git archive 05b7c78 | tar -x -C <dir>`), que tenía la misma API de
     * [VaultContainer.create], [VaultContainer.seal] y [OtpCrypto], y ejecutado allí con
     * `./gradlew --offline :app:testDebugUnitTest --tests '*LegacyFixtureGenerator*'`.
     */
    @Ignore("Solo se ejecuta a mano para producir un fixture nuevo; los existentes no se regeneran")
    @Test
    fun generateFixtures() {
        val outDir = File("src/test/resources/vault")
        val plainFile = File(outDir, "vault-v1-plain.bin")
        val bvdFile = File(outDir, "vault-v1.bvd")
        check(!plainFile.exists() && !bvdFile.exists()) { "Los fixtures ya existen y no se regeneran nunca" }

        val otpKey = OtpCrypto.newKey()
        val keyring = OtpCrypto.createKeyring(otpKey, keyringId, fixtureRecoveryCode.toCharArray(), fixtureKdfParams)
        val entries = expectedEntries.map { entry ->
            when (entry.id) {
                "a1" -> entry.copy(otp = OtpCrypto.seal(otpKey, keyringId, entry.id, otpSecretA1))
                "b2" -> entry.copy(otp = OtpCrypto.seal(otpKey, keyringId, entry.id, otpSecretB2))
                else -> entry
            }
        }
        otpKey.wipe()
        val data = VaultData(settings = expectedSettings, entries = entries, otpKeyring = keyring)

        val created = VaultContainer.create(fixturePassword.toCharArray(), data, fixtureKdfParams)
        val bvd = VaultContainer.seal(created.header, created.dek, created.data)
        created.dek.wipe()

        outDir.mkdirs()
        plainFile.writeBytes(VaultCodec.encode(data))
        bvdFile.writeBytes(bvd)

        val constantsDir = File("build/vault-fixtures").apply { mkdirs() }
        File(constantsDir, "constants.txt").writeText(
            listOf(
                "KEYRING_SALT=" + keyring.salt.toHex(),
                "KEYRING_WRAPPED_KEY=" + keyring.wrappedKey.toHex(),
                "SEALED_OTP_A1=" + entries[0].otp!!.bytes.toHex(),
                "SEALED_OTP_B2=" + entries[1].otp!!.bytes.toHex(),
            ).joinToString("\n", postfix = "\n"),
        )
    }
}
