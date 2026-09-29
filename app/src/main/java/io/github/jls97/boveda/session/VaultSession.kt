package io.github.jls97.boveda.session

import android.content.Context
import android.os.SystemClock
import io.github.jls97.boveda.core.crypto.wipe
import io.github.jls97.boveda.core.vault.CorruptedVaultException
import io.github.jls97.boveda.core.vault.DeviceBindingException
import io.github.jls97.boveda.core.vault.DeviceLayer
import io.github.jls97.boveda.core.vault.UnsupportedVaultException
import io.github.jls97.boveda.core.vault.VaultContainer
import io.github.jls97.boveda.core.vault.VaultData
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.core.vault.VaultException
import io.github.jls97.boveda.core.vault.VaultSettings
import io.github.jls97.boveda.core.vault.WrongPasswordException
import io.github.jls97.boveda.data.VaultStorage
import io.github.jls97.boveda.security.BiometricKeyManager
import io.github.jls97.boveda.security.DeviceKeyManager
import io.github.jls97.boveda.security.KeystoreKeys
import io.github.jls97.boveda.security.SecureClipboard
import io.github.jls97.boveda.security.UnlockThrottle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.Cipher

sealed interface VaultState {
    /** First run: there is no vault on this phone yet. */
    data object NoVault : VaultState

    data object Locked : VaultState

    data class Unlocked(val data: VaultData, val biometricEnabled: Boolean) : VaultState
}

sealed interface OperationResult {
    data object Success : OperationResult

    data object WrongPassword : OperationResult

    /** Too many wrong passwords: try again after [untilMillis] (epoch millis). */
    data class Throttled(val untilMillis: Long) : OperationResult

    data class Failure(val message: String) : OperationResult
}

/**
 * Holds the decrypted vault while it is unlocked and nothing else ever does.
 *
 * Threading: every method must be called on the main thread. Heavy work (Argon2id, encryption,
 * disk) runs on background dispatchers with private copies of the keys, so [lock] can wipe the
 * keys at any moment without corrupting a save that is already running.
 */
class VaultSession private constructor(
    private val storage: VaultStorage,
    private val deviceKeys: DeviceKeyManager,
    private val biometricKeys: BiometricKeyManager,
    private val throttle: UnlockThrottle,
    val clipboard: SecureClipboard,
    private val scope: CoroutineScope,
) {
    private class OpenVault(
        val header: VaultContainer.Header,
        val dek: ByteArray,
        val layerKey: ByteArray,
        var data: VaultData,
        var biometricEnabled: Boolean,
    ) {
        fun wipe() {
            dek.wipe()
            layerKey.wipe()
        }
    }

    private val _state = MutableStateFlow(if (storage.vaultExists()) VaultState.Locked else VaultState.NoVault)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val writeMutex = Mutex()
    private var open: OpenVault? = null
    private var lockCount = 0
    private var lastInteraction = SystemClock.elapsedRealtime()
    private var autoLockJob: Job? = null
    private var externalActivityExpected = false

    // region Lifecycle and auto-lock

    /** Call on every user interaction; it postpones the inactivity lock. */
    fun touch() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    /**
     * Call right before opening a system screen on purpose (the file picker for backups), so
     * "lock when leaving the app" does not lock in the middle of the operation. The inactivity
     * timeout and the screen-off lock still apply.
     */
    fun expectExternalActivity() {
        externalActivityExpected = true
    }

    fun onAppForeground() {
        externalActivityExpected = false
        val current = open ?: return
        val timeout = current.data.settings.autoLockSeconds
        if (timeout > 0 && SystemClock.elapsedRealtime() - lastInteraction >= timeout * 1_000L) lock()
    }

    fun onAppBackground() {
        val current = open ?: return
        if (current.data.settings.autoLockSeconds == 0 && !externalActivityExpected) lock()
    }

    fun isBiometricEnabled(): Boolean = biometricKeys.isEnabled()

    /** Forgets every key and decrypted value. Safe to call at any time and more than once. */
    fun lock() {
        lockCount++
        autoLockJob?.cancel()
        autoLockJob = null
        open?.wipe()
        open = null
        clipboard.clearIfPending()
        if (_state.value is VaultState.Unlocked) _state.value = VaultState.Locked
    }

    private fun startAutoLockTimer() {
        autoLockJob?.cancel()
        autoLockJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val current = open ?: break
                val timeout = current.data.settings.autoLockSeconds
                if (timeout > 0 && SystemClock.elapsedRealtime() - lastInteraction >= timeout * 1_000L) {
                    lock()
                    break
                }
            }
        }
    }

    private fun publish(current: OpenVault) {
        _state.value = VaultState.Unlocked(current.data, current.biometricEnabled)
    }

    private fun becomeUnlocked(newVault: OpenVault) {
        open?.wipe()
        open = newVault
        touch()
        publish(newVault)
        startAutoLockTimer()
    }

    // endregion

    // region Create, unlock, restore

    /** Creates a new vault protected by [password]. The array is wiped. */
    suspend fun create(password: CharArray): OperationResult = writeMutex.withLock {
        try {
            val lockCountAtStart = lockCount
            val newVault = withContext(Dispatchers.Default) {
                val layerKey = deviceKeys.loadOrCreate()
                try {
                    val created = VaultContainer.create(password, VaultData())
                    try {
                        val portable = VaultContainer.seal(created.header, created.dek, created.data)
                        storage.writeVault(DeviceLayer.seal(layerKey, portable))
                    } catch (e: Throwable) {
                        created.dek.wipe()
                        throw e
                    }
                    biometricKeys.disable()
                    throttle.reset()
                    OpenVault(created.header, created.dek, layerKey, created.data, biometricEnabled = false)
                } catch (e: Throwable) {
                    layerKey.wipe()
                    throw e
                }
            }
            finishUnlock(newVault, lockCountAtStart)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            password.wipe()
        }
    }

    /** Unlocks with the master password. The array is wiped. */
    suspend fun unlock(password: CharArray): OperationResult = writeMutex.withLock {
        try {
            val blockedUntil = throttle.blockedUntil()
            if (blockedUntil > 0) return@withLock OperationResult.Throttled(blockedUntil)
            val lockCountAtStart = lockCount
            val newVault = withContext(Dispatchers.Default) {
                try {
                    openStoredVault { portable -> VaultContainer.open(portable, password) }
                } catch (e: WrongPasswordException) {
                    null
                }
            }
            if (newVault == null) {
                val until = withContext(Dispatchers.IO) { throttle.recordFailure() }
                return@withLock if (until > 0) OperationResult.Throttled(until) else OperationResult.WrongPassword
            }
            withContext(Dispatchers.IO) { throttle.reset() }
            finishUnlock(newVault, lockCountAtStart)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            password.wipe()
        }
    }

    /** Cipher for the fingerprint prompt, or null if fingerprint unlock is not available. */
    fun biometricUnlockCipher(): Cipher? =
        try {
            biometricKeys.unlockCipher()
        } catch (e: Exception) {
            null
        }

    /** Finishes a fingerprint unlock with the cipher authorized by BiometricPrompt. */
    suspend fun unlockWithBiometric(authorizedCipher: Cipher): OperationResult = writeMutex.withLock {
        try {
            val lockCountAtStart = lockCount
            val newVault = withContext(Dispatchers.Default) {
                val dek = biometricKeys.unwrap(authorizedCipher)
                try {
                    openStoredVault { portable -> VaultContainer.openWithKey(portable, dek) }
                } finally {
                    dek.wipe()
                }
            }
            withContext(Dispatchers.IO) { throttle.reset() }
            finishUnlock(newVault, lockCountAtStart)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        }
    }

    /**
     * Replaces the vault on this phone with a backup. Works locked or unlocked, like clearing the
     * app's data in system settings would. The array is wiped.
     */
    suspend fun restoreBackup(backup: ByteArray, password: CharArray): OperationResult = writeMutex.withLock {
        try {
            val lockCountAtStart = lockCount
            val newVault = withContext(Dispatchers.Default) {
                val restored = try {
                    VaultContainer.open(backup, password)
                } catch (e: WrongPasswordException) {
                    null
                } ?: return@withContext null
                try {
                    val layerKey = deviceKeys.loadOrCreate()
                    try {
                        val portable = VaultContainer.seal(restored.header, restored.dek, restored.data)
                        storage.writeVault(DeviceLayer.seal(layerKey, portable))
                        biometricKeys.disable()
                        throttle.reset()
                        OpenVault(restored.header, restored.dek, layerKey, restored.data, biometricEnabled = false)
                    } catch (e: Throwable) {
                        layerKey.wipe()
                        throw e
                    }
                } catch (e: Throwable) {
                    restored.dek.wipe()
                    throw e
                }
            } ?: return@withLock OperationResult.WrongPassword
            finishUnlock(newVault, lockCountAtStart)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            password.wipe()
        }
    }

    /** Reads, unwraps the device layer and opens the vault file. Runs off the main thread. */
    private fun openStoredVault(openPortable: (ByteArray) -> VaultContainer.Opened): OpenVault {
        val layerKey = deviceKeys.load() ?: throw DeviceBindingException("Device key not found")
        try {
            val portable = DeviceLayer.open(layerKey, storage.readVault())
            val opened = openPortable(portable)
            return OpenVault(opened.header, opened.dek, layerKey, opened.data, biometricKeys.isEnabled())
        } catch (e: Throwable) {
            layerKey.wipe()
            throw e
        }
    }

    /**
     * Publishes the vault unless the phone locked while it was being opened. In that case the
     * vault file exists anyway (a create or restore already wrote it), so the state is Locked.
     */
    private fun finishUnlock(newVault: OpenVault, lockCountAtStart: Int): OperationResult {
        if (lockCount != lockCountAtStart) {
            newVault.wipe()
            _state.value = VaultState.Locked
            return OperationResult.Failure("Se bloqueó mientras se abría. Vuelve a intentarlo.")
        }
        becomeUnlocked(newVault)
        return OperationResult.Success
    }

    // endregion

    // region Changes while unlocked

    suspend fun saveEntry(entry: VaultEntry): OperationResult = update { data ->
        val index = data.entries.indexOfFirst { it.id == entry.id }
        val entries = if (index >= 0) data.entries.toMutableList().also { it[index] = entry } else data.entries + entry
        data.copy(entries = entries)
    }

    suspend fun deleteEntry(id: String): OperationResult = update { data ->
        data.copy(entries = data.entries.filterNot { it.id == id })
    }

    suspend fun updateSettings(settings: VaultSettings): OperationResult = update { it.copy(settings = settings) }

    private suspend fun update(transform: (VaultData) -> VaultData): OperationResult = writeMutex.withLock {
        val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
        val newData = transform(current.data)
        try {
            persist(current.header, current, newData)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock OperationResult.Failure(describe(e))
        }
        if (open === current) {
            current.data = newData
            publish(current)
        }
        OperationResult.Success
    }

    /** Encrypts and writes [data] using private copies of the keys. */
    private suspend fun persist(header: VaultContainer.Header, keysFrom: OpenVault, data: VaultData) {
        val dek = keysFrom.dek.copyOf()
        val layerKey = keysFrom.layerKey.copyOf()
        try {
            withContext(Dispatchers.IO) {
                val portable = VaultContainer.seal(header, dek, data)
                storage.writeVault(DeviceLayer.seal(layerKey, portable))
            }
        } finally {
            dek.wipe()
            layerKey.wipe()
        }
    }

    /** Checks the current password, then re-wraps the vault key under the new one. */
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): OperationResult =
        writeMutex.withLock {
            try {
                val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
                val dek = current.dek.copyOf()
                val newHeader = try {
                    withContext(Dispatchers.Default) {
                        if (!VaultContainer.verifyPassword(current.header, currentPassword)) {
                            null
                        } else {
                            VaultContainer.changePassword(dek, newPassword)
                        }
                    }
                } finally {
                    dek.wipe()
                } ?: return@withLock OperationResult.WrongPassword
                persist(newHeader, current, current.data)
                if (open === current) {
                    val updated = OpenVault(
                        newHeader,
                        current.dek.copyOf(),
                        current.layerKey.copyOf(),
                        current.data,
                        current.biometricEnabled,
                    )
                    current.wipe()
                    open = updated
                    publish(updated)
                }
                OperationResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Failure(describe(e))
            } finally {
                currentPassword.wipe()
                newPassword.wipe()
            }
        }

    /** The vault as a portable backup file, protected only by the master password. */
    suspend fun exportBackup(): ByteArray? = writeMutex.withLock {
        val current = open ?: return@withLock null
        val dek = current.dek.copyOf()
        try {
            withContext(Dispatchers.Default) { VaultContainer.seal(current.header, dek, current.data) }
        } finally {
            dek.wipe()
        }
    }

    /** Cipher for the fingerprint prompt that turns on fingerprint unlock. */
    fun biometricEnrollmentCipher(): Cipher? =
        try {
            biometricKeys.enrollmentCipher()
        } catch (e: Exception) {
            null
        }

    suspend fun enableBiometric(authorizedCipher: Cipher): OperationResult = writeMutex.withLock {
        val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
        val dek = current.dek.copyOf()
        try {
            withContext(Dispatchers.Default) { biometricKeys.finishEnrollment(authorizedCipher, dek) }
            current.biometricEnabled = true
            if (open === current) publish(current)
            OperationResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            biometricKeys.disable()
            OperationResult.Failure("No se pudo activar la huella")
        } finally {
            dek.wipe()
        }
    }

    fun disableBiometric() {
        biometricKeys.disable()
        val current = open ?: return
        current.biometricEnabled = false
        publish(current)
    }

    // endregion

    private fun describe(e: Exception): String = when (e) {
        is DeviceBindingException ->
            "La bóveda no se puede abrir en este teléfono: su clave de hardware no está disponible. " +
                "Restaura una copia de seguridad."
        is CorruptedVaultException -> "El archivo está dañado o ha sido modificado."
        is UnsupportedVaultException -> "Formato de bóveda no compatible."
        is VaultException -> "Error de la bóveda."
        is IOException -> "No se pudo leer o escribir el archivo."
        is GeneralSecurityException, is ProviderException -> "El almacén de claves del sistema rechazó la operación."
        else -> "Error inesperado."
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope): VaultSession {
            val appContext = context.applicationContext
            val storage = VaultStorage(appContext)
            val keys = KeystoreKeys(appContext)
            return VaultSession(
                storage = storage,
                deviceKeys = DeviceKeyManager(keys, storage.layerKeyFile),
                biometricKeys = BiometricKeyManager(keys, storage.biometricKeyFile),
                throttle = UnlockThrottle(appContext),
                clipboard = SecureClipboard(appContext, scope),
                scope = scope,
            )
        }
    }
}
