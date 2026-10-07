package io.github.jls97.boveda.session

import io.github.jls97.boveda.core.crypto.wipe
import io.github.jls97.boveda.core.vault.DeviceLayer
import io.github.jls97.boveda.core.vault.VaultContainer
import io.github.jls97.boveda.core.vault.VaultData
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.core.vault.WrongPasswordException
import io.github.jls97.boveda.data.VaultFiles
import io.github.jls97.boveda.security.FingerprintKeys
import io.github.jls97.boveda.security.IntegrityRecord
import io.github.jls97.boveda.security.IntegrityStore
import io.github.jls97.boveda.security.KeySecurityLevel
import io.github.jls97.boveda.security.KeystoreUnavailableException
import io.github.jls97.boveda.security.LayerKeys
import io.github.jls97.boveda.security.OtpKeys
import io.github.jls97.boveda.security.ThrottleClock
import io.github.jls97.boveda.security.ThrottleState
import io.github.jls97.boveda.security.ThrottleStore
import io.github.jls97.boveda.security.UnlockThrottle
import io.github.jls97.boveda.security.VaultClipboard
import io.github.jls97.boveda.security.VaultIntegrity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.CountDownLatch
import javax.crypto.Cipher

/**
 * La sesión con archivos, claves y reloj falsos en memoria (A-04, M-07, I-34, R07-2, R07-5): las
 * carreras con [VaultSession.lock] y la propagación de un Keystore que no responde se ejercitan de
 * verdad, no solo por lectura. La criptografía es la real: Argon2id con [io.github.jls97.boveda.core.crypto.KdfParams.DEFAULT],
 * porque la sesión reescribe al desbloquear cualquier cabecera más barata, así que cada test tarda
 * unos segundos. El hilo de `runBlocking` hace de hilo principal.
 */
class VaultSessionTest {

    private companion object {
        const val PASSWORD = "contraseña-actual"
        const val NEW_PASSWORD = "contraseña-nueva"
        const val BACKUP_PASSWORD = "contraseña-de-la-copia"
        const val LOCKED_WHILE_CHANGING = "Se bloqueó mientras se cambiaba la contraseña. No se ha escrito nada."
        const val KEYSTORE_DID_NOT_ANSWER = "El almacén de claves del teléfono no respondió. Vuelve a intentarlo o reinicia el teléfono."

        val data = VaultData(
            entries = listOf(VaultEntry(id = "a1", title = "Banco", username = "yo", password = "secreta", createdAt = 1, updatedAt = 2)),
        )
        val backupData = VaultData(
            entries = listOf(VaultEntry(id = "z9", title = "Copia", username = "otro", password = "otra", createdAt = 3, updatedAt = 4)),
        )

        /** Bóveda portable (sin capa de dispositivo) de [data] bajo [PASSWORD]; una sola derivación para toda la clase. */
        val portable: ByteArray by lazy {
            val created = VaultContainer.create(PASSWORD.toCharArray(), data)
            VaultContainer.seal(created.header, created.dek, created.data).also { created.dek.wipe() }
        }

        /** Copia de seguridad de [backupData] bajo [BACKUP_PASSWORD]. */
        val backup: ByteArray by lazy {
            val created = VaultContainer.create(BACKUP_PASSWORD.toCharArray(), backupData)
            VaultContainer.seal(created.header, created.dek, created.data).also { created.dek.wipe() }
        }
    }

    /** Todo lo que hacen los dobles, en orden, para afirmar sobre la secuencia (huella fuera antes de escribir...). */
    private val events = mutableListOf<String>()

    private inner class FakeFiles : VaultFiles {
        var vault: ByteArray? = null
        var previous: ByteArray? = null
        var vaultWrites = 0

        override fun vaultExists(): Boolean = vault != null

        override fun readVault(): ByteArray = vault?.copyOf() ?: throw IOException("Sin bóveda")

        override fun writeVault(bytes: ByteArray) {
            vault = bytes.copyOf()
            vaultWrites++
            events += "writeVault"
        }

        override fun previousVaultExists(): Boolean = previous != null

        override fun readPreviousVault(): ByteArray = previous?.copyOf() ?: throw IOException("Sin copia previa")

        override fun writePreviousVault(bytes: ByteArray) {
            previous = bytes.copyOf()
            events += "writePreviousVault"
        }

        override fun deletePreviousVault() {
            previous = null
            events += "deletePreviousVault"
        }
    }

    /** Una clave de capa fija; [failWith] simula un Keystore que no responde (fallo transitorio). */
    private class FakeLayerKeys(private val key: ByteArray) : LayerKeys {
        var failWith: Exception? = null

        override fun load(): ByteArray? {
            failWith?.let { throw it }
            return key.copyOf()
        }

        override fun loadOrCreate(beforeCreate: () -> Unit): ByteArray {
            failWith?.let { throw it }
            return key.copyOf()
        }

        override fun securityLevel(): KeySecurityLevel = KeySecurityLevel.TEE
    }

    private inner class FakeFingerprintKeys(var enabled: Boolean) : FingerprintKeys {
        override fun isEnabled(): Boolean = enabled

        override fun enrollmentCipher(): Cipher = throw GeneralSecurityException("Sin huella en la JVM")

        override fun finishEnrollment(authorizedCipher: Cipher, dek: ByteArray) = throw GeneralSecurityException("Sin huella en la JVM")

        override fun unlockCipher(): Cipher? = null

        override fun unwrap(authorizedCipher: Cipher): ByteArray = throw GeneralSecurityException("Sin huella en la JVM")

        override fun disable() {
            enabled = false
            events += "fingerprint.disable"
        }
    }

    private inner class FakeOtpKeys : OtpKeys {
        override fun isReadyFor(keyringId: ByteArray): Boolean = false

        override fun enrollmentCipher(): Cipher = throw GeneralSecurityException("Sin huella en la JVM")

        override fun finishEnrollment(authorizedCipher: Cipher, keyringId: ByteArray, otpKey: ByteArray) =
            throw GeneralSecurityException("Sin huella en la JVM")

        override fun unlockCipher(keyringId: ByteArray): Cipher? = null

        override fun unwrap(authorizedCipher: Cipher, keyringId: ByteArray): ByteArray = throw GeneralSecurityException("Sin huella en la JVM")

        override fun disable() {
            events += "otp.disable"
        }
    }

    /** [onClear] se ejecuta dentro de `throttle.reset()`: el primer punto observable tras verificar la contraseña. */
    private class FakeThrottleStore : ThrottleStore {
        private var state = ThrottleState()
        var onClear: (() -> Unit)? = null

        override fun load(): ThrottleState = state

        override fun save(state: ThrottleState) {
            this.state = state
        }

        override fun clear() {
            state = ThrottleState()
            onClear?.invoke()
        }
    }

    private class FakeThrottleClock : ThrottleClock {
        override fun elapsedRealtime(): Long = 5_000_000L

        override fun wallClock(): Long = 1_700_000_000_000L

        override fun bootCount(): Int = 1
    }

    private class FakeIntegrityStore : IntegrityStore {
        var record: IntegrityRecord? = null

        override fun load(): IntegrityRecord? = record

        override fun save(record: IntegrityRecord) {
            this.record = record
        }
    }

    private inner class FakeClipboard : VaultClipboard {
        override fun copy(text: String, clearAfterSeconds: Int) = Unit

        override fun clearIfPending() {
            events += "clipboard.clear"
        }
    }

    private val layerKey = ByteArray(32) { (it * 11 + 5).toByte() }
    private val files = FakeFiles()
    private val layerKeys = FakeLayerKeys(layerKey)
    private val fingerprint = FakeFingerprintKeys(enabled = false)
    private val throttleStore = FakeThrottleStore()
    private val integrityStore = FakeIntegrityStore()
    private val integrity = VaultIntegrity(integrityStore)

    /** Reloj monótono de la sesión: parado, así el temporizador de bloqueo por inactividad nunca salta. */
    private var now = 1_000_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // El teléfono ya tiene la bóveda de [data] sellada con su clave de capa y registrada como la última escrita.
        files.vault = DeviceLayer.seal(layerKey, portable)
        integrity.recordWrite(files.vault!!)
    }

    private fun newSession() = VaultSession(
        storage = files,
        deviceKeys = layerKeys,
        biometricKeys = fingerprint,
        otpKeys = FakeOtpKeys(),
        throttle = UnlockThrottle(throttleStore, FakeThrottleClock()),
        integrity = integrity,
        clipboard = FakeClipboard(),
        scope = scope,
        elapsedRealtime = { now },
    )

    @After
    fun stopAutoLockTimers() {
        scope.cancel()
    }

    private fun chars(text: String): CharArray = text.toCharArray()

    private fun unlocked(session: VaultSession): VaultState.Unlocked = session.state.value as VaultState.Unlocked

    /** La bóveda portable que hay en disco, abierta con la clave de capa. */
    private fun portableOnDisk(): ByteArray = DeviceLayer.open(layerKey, checkNotNull(files.vault))

    private fun assertOpensWith(password: String, portable: ByteArray): VaultData {
        val opened = VaultContainer.open(portable, chars(password))
        opened.dek.wipe()
        return opened.data
    }

    private fun assertDoesNotOpenWith(password: String, portable: ByteArray) {
        assertThrows(WrongPasswordException::class.java) { VaultContainer.open(portable, chars(password)) }
    }

    @Test
    fun lockDuringChangeMasterPasswordWritesNothingAndTheOldPasswordStillOpensTheFile() = runBlocking {
        fingerprint.enabled = true
        val session = newSession()
        assertEquals(VaultState.Locked, session.state.value)
        assertEquals(OperationResult.Success, session.unlock(chars(PASSWORD)))
        assertEquals(data, unlocked(session).data)
        val fileBefore = checkNotNull(files.vault).copyOf()
        val writesBefore = files.vaultWrites
        events.clear()

        // La sesión llama a throttle.reset() justo después de verificar la contraseña y derivar la
        // nueva (Argon2id, en otro hilo) y justo antes de comprobar si sigue abierta: el bloqueo
        // llega ahí, desde el hilo principal del test, como lo haría la pantalla apagándose.
        val lockRequested = CompletableDeferred<Unit>()
        val lockDone = CountDownLatch(1)
        throttleStore.onClear = {
            lockRequested.complete(Unit)
            lockDone.await()
        }
        val change = async { session.changeMasterPassword(chars(PASSWORD), chars(NEW_PASSWORD)) }
        lockRequested.await()
        session.lock()
        assertEquals(VaultState.Locked, session.state.value)
        lockDone.countDown()

        assertEquals(OperationResult.Failure(LOCKED_WHILE_CHANGING), change.await())
        assertEquals(VaultState.Locked, session.state.value)
        assertEquals("No se ha escrito nada", writesBefore, files.vaultWrites)
        assertArrayEquals(fileBefore, files.vault)
        assertFalse("La huella no se toca si no se escribe", "fingerprint.disable" in events)
        assertTrue(fingerprint.enabled)

        val portable = portableOnDisk()
        assertEquals(data, assertOpensWith(PASSWORD, portable))
        assertDoesNotOpenWith(NEW_PASSWORD, portable)

        // Nada quedó a medias: la sesión vuelve a abrir con la contraseña de siempre y entonces sí cambia.
        throttleStore.onClear = null
        assertEquals(OperationResult.Success, session.unlock(chars(PASSWORD)))
        assertEquals(OperationResult.Success, session.changeMasterPassword(chars(PASSWORD), chars(NEW_PASSWORD)))
        assertEquals(data, assertOpensWith(NEW_PASSWORD, portableOnDisk()))
    }

    @Test
    fun changeMasterPasswordTurnsOffTheFingerprintBeforeRewritingTheFileUnderTheNewPassword() = runBlocking {
        fingerprint.enabled = true
        val session = newSession()
        assertEquals(OperationResult.Success, session.unlock(chars(PASSWORD)))
        assertTrue(unlocked(session).biometricEnabled)
        val writesBefore = files.vaultWrites
        events.clear()

        assertEquals(OperationResult.Success, session.changeMasterPassword(chars(PASSWORD), chars(NEW_PASSWORD)))

        // biometric.key envolvía la DEK antigua: fuera ANTES de escribir, para que un fallo de la
        // escritura deje la contraseña antigua válida y la huella simplemente apagada.
        assertEquals(listOf("fingerprint.disable", "writeVault"), events)
        assertEquals(writesBefore + 1, files.vaultWrites)
        assertFalse(fingerprint.enabled)
        val state = unlocked(session)
        assertFalse(state.biometricEnabled)
        assertEquals(data, state.data)

        val portable = portableOnDisk()
        assertEquals(data, assertOpensWith(NEW_PASSWORD, portable))
        assertDoesNotOpenWith(PASSWORD, portable)

        // La sesión sigue con las claves nuevas: lo que se guarde a partir de ahora lo abre la contraseña nueva.
        val added = VaultEntry(id = "b2", title = "Correo", createdAt = 5, updatedAt = 6)
        assertEquals(OperationResult.Success, session.saveEntry(added))
        assertEquals(data.entries + added, assertOpensWith(NEW_PASSWORD, portableOnDisk()).entries)
        assertEquals(data.entries + added, unlocked(session).data.entries)

        // Con la contraseña equivocada no se cambia nada ni se escribe.
        val writesAfter = files.vaultWrites
        assertEquals(OperationResult.WrongPassword, session.changeMasterPassword(chars(PASSWORD), chars("otra")))
        assertEquals(writesAfter, files.vaultWrites)
    }

    @Test
    fun restoreBackupFailsWithoutTouchingTheFilesWhenTheKeystoreDoesNotAnswer() = runBlocking {
        val session = newSession()
        assertEquals(OperationResult.Success, session.unlock(chars(PASSWORD)))
        val fileBefore = checkNotNull(files.vault).copyOf()
        val writesBefore = files.vaultWrites
        events.clear()

        // La clave de capa está intacta pero el Keystore no contesta: es un fallo transitorio, así
        // que no se crea una clave nueva (que dejaría ilegible la copia para deshacer) ni se escribe.
        layerKeys.failWith = KeystoreUnavailableException("Keystore ocupado")
        val result = session.restoreBackup(backup, chars(BACKUP_PASSWORD), currentPassword = chars(PASSWORD))
        assertEquals(OperationResult.Failure(KEYSTORE_DID_NOT_ANSWER), result)
        assertEquals(writesBefore, files.vaultWrites)
        assertArrayEquals(fileBefore, files.vault)
        assertNull("No se guarda ninguna copia previa de una restauración que no ocurrió", files.previous)
        assertFalse(session.canUndoRestore())
        assertFalse("writePreviousVault" in events)
        assertFalse("fingerprint.disable" in events)
        // La bóveda abierta sigue tal cual.
        assertEquals(data, unlocked(session).data)
        assertEquals(data, assertOpensWith(PASSWORD, portableOnDisk()))

        // En cuanto el Keystore responde, la misma restauración sale con la misma clave de capa.
        layerKeys.failWith = null
        val restored = session.restoreBackup(backup, chars(BACKUP_PASSWORD), currentPassword = chars(PASSWORD))
        assertEquals(OperationResult.Restored(masterPasswordChanged = true, hadUndo = true), restored)
        assertEquals(backupData, unlocked(session).data)
        assertEquals(backupData, assertOpensWith(BACKUP_PASSWORD, portableOnDisk()))
        assertArrayEquals(fileBefore, files.previous)
    }

    @Test
    fun undoRestorePutsTheReplacedVaultBackByteForByteAndTheIntegrityRecordDoesNotWarn() = runBlocking {
        fingerprint.enabled = true
        val session = newSession()
        assertEquals(OperationResult.Success, session.unlock(chars(PASSWORD)))
        assertFalse(unlocked(session).integrityWarning)
        val original = checkNotNull(files.vault).copyOf()

        val restored = session.restoreBackup(backup, chars(BACKUP_PASSWORD), currentPassword = chars(PASSWORD))
        assertEquals(OperationResult.Restored(masterPasswordChanged = true, hadUndo = true), restored)
        assertArrayEquals("La bóveda sustituida se guarda tal cual", original, files.previous)
        assertFalse(original.contentEquals(files.vault))
        assertEquals(backupData, unlocked(session).data)
        assertFalse("biometric.key envolvía la DEK de la bóveda que se fue", unlocked(session).biometricEnabled)
        assertTrue(session.canUndoRestore())
        val restoredFile = checkNotNull(files.vault).copyOf()

        assertEquals(OperationResult.Success, session.undoRestore())
        assertArrayEquals("vault.bin vuelve byte a byte", original, files.vault)
        assertArrayEquals("Deshacer el deshacer sigue siendo posible", restoredFile, files.previous)
        assertEquals("Las claves en memoria eran de la bóveda que se fue", VaultState.Locked, session.state.value)
        assertTrue(session.canUndoRestore())

        // El registro de integridad describe el archivo que hay en disco: al abrir no avisa.
        assertTrue(integrity.isLatest(checkNotNull(files.vault)))
        assertEquals(OperationResult.Success, session.unlock(chars(PASSWORD)))
        val back = unlocked(session)
        assertFalse(back.integrityWarning)
        assertEquals(data, back.data)
        assertFalse(back.biometricEnabled)
    }
}
