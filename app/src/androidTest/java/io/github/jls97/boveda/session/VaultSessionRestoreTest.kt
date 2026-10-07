package io.github.jls97.boveda.session

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.data.VaultStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Restaurar una copia con una bóveda ya en el teléfono, con la sesión real sobre un almacenamiento
 * temporal y el Keystore del dispositivo (B-31, R01-1..3): desde bloqueada con la contraseña
 * actual o forzando, desde abierta con la contraseña actual, y el deshacer que la sesión conserva.
 * RestorePolicyTest prueba la política pura; esto prueba que el cableado llega a ella.
 *
 * La clave de capa vive en el Keystore con un alias fijo, así que crear una bóveda aquí sustituye
 * la de la app de depuración: el test solo corre cuando esa app no tiene bóveda que perder.
 */
@RunWith(AndroidJUnit4::class)
class VaultSessionRestoreTest {

    private lateinit var dir: File
    private lateinit var context: IsolatedContext
    private lateinit var scope: CoroutineScope
    private lateinit var session: VaultSession

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Requiere una instalación sin bóveda creada", !VaultStorage(target).vaultExists())
        dir = File(target.cacheDir, "restore-test-${System.nanoTime()}").apply { mkdirs() }
        context = IsolatedContext(target, dir)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        // Sin exigir bloqueo de pantalla: el emulador no suele tenerlo y aquí no es lo que se prueba.
        session = VaultSession.create(context, scope, deviceSecure = { true })
    }

    @After
    fun tearDown() {
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
        if (::context.isInitialized) context.clearTestPreferences()
    }

    /** Una bóveda con [PASSWORD_A], una copia de ella y, en el teléfono, la misma bóveda ya con [PASSWORD_B] y una entrada. */
    private suspend fun vaultWithAnOlderBackup(): ByteArray {
        assertEquals(OperationResult.Success, session.create(PASSWORD_A.toCharArray()))
        val backup = requireNotNull(session.exportBackup())
        assertEquals(OperationResult.Success, session.changeMasterPassword(PASSWORD_A.toCharArray(), PASSWORD_B.toCharArray()))
        assertEquals(OperationResult.Success, session.saveEntry(VaultEntry(id = "e1", title = "Correo", createdAt = 1L, updatedAt = 1L)))
        return backup
    }

    private val unlocked get() = session.state.value as VaultState.Unlocked

    @Test
    fun lockedVaultIsRestoredWithTheCurrentPasswordAndKeepsAnUndo() = runBlocking(Dispatchers.Main) {
        val backup = vaultWithAnOlderBackup()
        session.lock()
        assertEquals(VaultState.Locked, session.state.value)

        // Lo que hacía la interfaz antes: ni contraseña actual ni forzar. Se rechaza.
        assertTrue(session.restoreBackup(backup, PASSWORD_A.toCharArray(), null, forceWithoutCurrent = false) is OperationResult.Failure)
        assertEquals(VaultState.Locked, session.state.value)
        assertEquals(
            OperationResult.WrongCurrentPassword,
            session.restoreBackup(backup, PASSWORD_A.toCharArray(), "otra contraseña".toCharArray(), forceWithoutCurrent = false),
        )

        val result = session.restoreBackup(backup, PASSWORD_A.toCharArray(), PASSWORD_B.toCharArray(), forceWithoutCurrent = false)
        assertEquals(OperationResult.Restored(masterPasswordChanged = true, hadUndo = true), result)
        assertTrue(session.state.value is VaultState.Unlocked)
        assertTrue("la copia no tenía la entrada", unlocked.data.entries.isEmpty())
        assertTrue(session.canUndoRestore())
    }

    @Test
    fun unlockedVaultIsRestoredOnlyWithTheCurrentPassword() = runBlocking(Dispatchers.Main) {
        val backup = vaultWithAnOlderBackup()
        assertTrue(session.state.value is VaultState.Unlocked)

        assertTrue(session.restoreBackup(backup, PASSWORD_A.toCharArray(), null, forceWithoutCurrent = false) is OperationResult.Failure)
        assertTrue("forzar no vale con la bóveda abierta", session.restoreBackup(backup, PASSWORD_A.toCharArray(), null, forceWithoutCurrent = true) is OperationResult.Failure)
        assertEquals(1, unlocked.data.entries.size)

        val result = session.restoreBackup(backup, PASSWORD_A.toCharArray(), PASSWORD_B.toCharArray(), forceWithoutCurrent = false)
        assertEquals(OperationResult.Restored(masterPasswordChanged = true, hadUndo = true), result)
        assertTrue(unlocked.data.entries.isEmpty())
        assertTrue(session.canUndoRestore())

        // Volver a restaurar la misma copia (ahora con su propia contraseña como actual) no pisa
        // la copia guardada para deshacer: la bóveda de antes sigue siendo la que vuelve.
        assertEquals(
            OperationResult.Restored(masterPasswordChanged = false, hadUndo = true),
            session.restoreBackup(backup, PASSWORD_A.toCharArray(), PASSWORD_A.toCharArray(), forceWithoutCurrent = false),
        )
        assertEquals(OperationResult.Success, session.undoRestore())
        assertEquals(OperationResult.Success, session.unlock(PASSWORD_B.toCharArray()))
        assertEquals(1, unlocked.data.entries.size)
    }

    @Test
    fun forcedRestoreFromLockedCanBeUndoneAndTheUndoDiscarded() = runBlocking(Dispatchers.Main) {
        val backup = vaultWithAnOlderBackup()
        session.lock()

        val result = session.restoreBackup(backup, PASSWORD_A.toCharArray(), null, forceWithoutCurrent = true)
        assertEquals(OperationResult.Restored(masterPasswordChanged = true, hadUndo = true), result)
        assertTrue(session.state.value is VaultState.Unlocked)
        assertTrue(unlocked.data.entries.isEmpty())
        assertTrue(session.canUndoRestore())

        assertEquals(OperationResult.Success, session.undoRestore())
        assertEquals("deshacer bloquea la sesión", VaultState.Locked, session.state.value)
        assertTrue("la restaurada queda guardada para volver a cambiar", session.canUndoRestore())
        assertEquals(OperationResult.WrongPassword, session.unlock(PASSWORD_A.toCharArray()))
        assertEquals(OperationResult.Success, session.unlock(PASSWORD_B.toCharArray()))
        assertEquals(listOf("Correo"), unlocked.data.entries.map { it.title })

        session.discardUndo()
        assertFalse(session.canUndoRestore())
        assertTrue(session.undoRestore() is OperationResult.Failure)
    }

    @Test
    fun firstRunRestoreNeedsNoCurrentPasswordAndRefusesWithAVault() = runBlocking(Dispatchers.Main) {
        assertEquals(OperationResult.Success, session.create(PASSWORD_A.toCharArray()))
        val backup = requireNotNull(session.exportBackup())
        assertTrue(session.restoreBackupFirstRun(backup, PASSWORD_A.toCharArray()) is OperationResult.Failure)
        session.lock()
        assertTrue(session.restoreBackupFirstRun(backup, PASSWORD_A.toCharArray()) is OperationResult.Failure)
        // Sin bóveda en el teléfono (como en el primer arranque): nada que proteger ni que deshacer.
        VaultStorage(context).run {
            deleteArtifact(vaultFile)
            deletePreviousVault()
        }
        assertEquals(
            OperationResult.Restored(masterPasswordChanged = false, hadUndo = false),
            session.restoreBackupFirstRun(backup, PASSWORD_A.toCharArray()),
        )
        assertFalse(session.canUndoRestore())
    }

    @Test
    fun theOtpKeyFileOutlivesARestoreThatCanBeUndoneAndGoesWithTheDiscard() = runBlocking(Dispatchers.Main) {
        val backup = vaultWithAnOlderBackup()
        // Un otp.key cualquiera: la sesión solo mira si es de la bóveda restaurada (no lo es).
        val otpKey = VaultStorage(context).otpKeyFile
        otpKey.writeBytes(ByteArray(8))
        session.lock()

        assertEquals(
            OperationResult.Restored(masterPasswordChanged = true, hadUndo = true),
            session.restoreBackup(backup, PASSWORD_A.toCharArray(), null, forceWithoutCurrent = true),
        )
        assertTrue("se conserva mientras la bóveda anterior pueda volver (R01-8)", otpKey.exists())
        assertEquals(OtpAccess.NONE, unlocked.otpAccess)

        session.discardUndo()
        assertFalse("huérfano una vez descartada la bóveda anterior", otpKey.exists())
    }

    @Test
    fun withoutASecureLockScreenNothingIsCreatedNorRestored() = runBlocking(Dispatchers.Main) {
        val backup = vaultWithAnOlderBackup()
        val insecure = VaultSession.create(context, scope, deviceSecure = { false })
        assertEquals(OperationResult.Failure(VaultSession.SECURE_LOCK_SCREEN_REQUIRED), insecure.create(PASSWORD_A.toCharArray()))
        assertEquals(
            OperationResult.Failure(VaultSession.SECURE_LOCK_SCREEN_REQUIRED),
            insecure.restoreBackup(backup, PASSWORD_A.toCharArray(), PASSWORD_B.toCharArray(), forceWithoutCurrent = false),
        )
        assertFalse(insecure.canUndoRestore())
    }

    /** Archivos y preferencias propios, para no tocar los de la app de depuración. */
    private class IsolatedContext(base: Context, private val dir: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getNoBackupFilesDir(): File = dir

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(PREFIX + name, mode)

        fun clearTestPreferences() {
            for (name in listOf("unlock_throttle", "unlock_throttle_kept", "vault_integrity")) {
                deleteSharedPreferences(PREFIX + name)
            }
        }

        private companion object {
            const val PREFIX = "restore-test."
        }
    }

    private companion object {
        const val PASSWORD_A = "una frase larga de prueba 1"
        const val PASSWORD_B = "otra frase larga de prueba 2"
    }
}
