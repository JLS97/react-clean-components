package io.github.jls97.boveda.session

import android.content.Context
import android.os.SystemClock
import io.github.jls97.boveda.core.crypto.wipe
import io.github.jls97.boveda.core.otp.OtpCrypto
import io.github.jls97.boveda.core.otp.OtpSecret
import io.github.jls97.boveda.core.otp.WrongRecoveryCodeException
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
import io.github.jls97.boveda.security.KeySecurityLevel
import io.github.jls97.boveda.security.KeystoreKeys
import io.github.jls97.boveda.security.KeystoreUnavailableException
import io.github.jls97.boveda.security.OtpKeyManager
import io.github.jls97.boveda.security.SecureClipboard
import io.github.jls97.boveda.security.UnlockThrottle
import io.github.jls97.boveda.security.VaultIntegrity
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
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher

sealed interface VaultState {
    /** First run: there is no vault on this phone yet. */
    data object NoVault : VaultState

    data object Locked : VaultState

    data class Unlocked(
        val data: VaultData,
        val biometricEnabled: Boolean,
        val otpAccess: OtpAccess = OtpAccess.NONE,
        /**
         * The file opened is not the last one saved on this phone (see
         * [io.github.jls97.boveda.security.VaultIntegrity]): someone put an older copy back. The UI
         * warns; nothing is blocked.
         */
        val integrityWarning: Boolean = false,
        /**
         * Where the Keystore key that binds the vault to this phone lives (StrongBox, TEE,
         * Software or desconocido), as the system reports it. For the settings screen.
         */
        val deviceKeySecurityLevel: String = KeySecurityLevel.UNKNOWN.label,
        /** Set when that key is software only: the vault is not bound to the phone's security chip. */
        val deviceKeyWarning: String? = null,
    ) : VaultState
}

/** Whether this phone can open the 2FA codes. */
enum class OtpAccess {
    /** No 2FA code has been saved yet. */
    NONE,

    /** Every code opens with a fingerprint. */
    READY,

    /**
     * The vault has codes but this phone has no usable fingerprint key for them (a restored
     * backup, a new fingerprint, another phone): they come back with the recovery code.
     */
    LOCKED,
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
    private val otpKeys: OtpKeyManager,
    private val throttle: UnlockThrottle,
    private val integrity: VaultIntegrity,
    val clipboard: SecureClipboard,
    private val scope: CoroutineScope,
) {
    private class OpenVault(
        val header: VaultContainer.Header,
        val dek: ByteArray,
        val layerKey: ByteArray,
        var data: VaultData,
        var biometricEnabled: Boolean,
        /** This phone holds a fingerprint-protected copy of the 2FA key of [data]. */
        var otpOnDevice: Boolean,
        /** The file this vault was opened from was not the last one written on this phone. */
        val integrityWarning: Boolean = false,
        /** Where the Keystore key that wraps [layerKey] lives. */
        val deviceKeySecurityLevel: KeySecurityLevel = KeySecurityLevel.UNKNOWN,
    ) {
        fun wipe() {
            dek.wipe()
            layerKey.wipe()
        }
    }

    private val _state = MutableStateFlow(if (storage.vaultExists()) VaultState.Locked else VaultState.NoVault)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _resumeTicks = MutableStateFlow(0)

    /** Changes every time one of Bóveda's screens comes back to the front. */
    val resumeTicks: StateFlow<Int> = _resumeTicks.asStateFlow()

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

    fun onAppResumed() {
        _resumeTicks.value++
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
        val otpAccess = when {
            current.data.otpKeyring == null -> OtpAccess.NONE
            current.otpOnDevice -> OtpAccess.READY
            else -> OtpAccess.LOCKED
        }
        _state.value = VaultState.Unlocked(
            current.data,
            current.biometricEnabled,
            otpAccess,
            current.integrityWarning,
            deviceKeySecurityLevel = current.deviceKeySecurityLevel.label,
            deviceKeyWarning = SOFTWARE_KEY_WARNING.takeIf { current.deviceKeySecurityLevel == KeySecurityLevel.SOFTWARE },
        )
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
                        writeVault(DeviceLayer.seal(layerKey, portable))
                    } catch (e: Throwable) {
                        created.dek.wipe()
                        throw e
                    }
                    biometricKeys.disable()
                    otpKeys.disable()
                    throttle.reset()
                    OpenVault(
                        created.header,
                        created.dek,
                        layerKey,
                        created.data,
                        biometricEnabled = false,
                        otpOnDevice = false,
                        deviceKeySecurityLevel = deviceKeys.securityLevel(),
                    )
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
            val blockedUntil = withContext(Dispatchers.IO) { throttle.blockedUntil() }
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
            // Same throttle as unlock: checking the backup's password is also a password oracle.
            val blockedUntil = withContext(Dispatchers.IO) { throttle.blockedUntil() }
            if (blockedUntil > 0) return@withLock OperationResult.Throttled(blockedUntil)
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
                        writeVault(DeviceLayer.seal(layerKey, portable))
                        biometricKeys.disable()
                        throttle.reset()
                        OpenVault(
                            restored.header,
                            restored.dek,
                            layerKey,
                            restored.data,
                            biometricEnabled = false,
                            // An older backup of this same vault keeps working with the fingerprint.
                            otpOnDevice = otpOnDevice(restored.data),
                            deviceKeySecurityLevel = deviceKeys.securityLevel(),
                        )
                    } catch (e: Throwable) {
                        layerKey.wipe()
                        throw e
                    }
                } catch (e: Throwable) {
                    restored.dek.wipe()
                    throw e
                }
            }
            if (newVault == null) {
                val until = withContext(Dispatchers.IO) { throttle.recordFailure() }
                return@withLock if (until > 0) OperationResult.Throttled(until) else OperationResult.WrongPassword
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

    /** Reads, unwraps the device layer and opens the vault file. Runs off the main thread. */
    private fun openStoredVault(openPortable: (ByteArray) -> VaultContainer.Opened): OpenVault {
        val layerKey = deviceKeys.load() ?: throw DeviceBindingException("Device key not found")
        try {
            val sealed = storage.readVault()
            val portable = DeviceLayer.open(layerKey, sealed)
            val opened = openPortable(portable)
            return OpenVault(
                opened.header,
                opened.dek,
                layerKey,
                opened.data,
                biometricKeys.isEnabled(),
                otpOnDevice(opened.data),
                integrityWarning = !integrity.isLatest(sealed),
                deviceKeySecurityLevel = deviceKeys.securityLevel(),
            )
        } catch (e: Throwable) {
            layerKey.wipe()
            throw e
        }
    }

    private fun otpOnDevice(data: VaultData): Boolean {
        val keyring = data.otpKeyring ?: return false
        return try {
            otpKeys.isReadyFor(keyring.id)
        } catch (e: Exception) {
            false
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

    /**
     * Encrypts and writes [data] using private copies of the keys of [keysFrom]. Call it right after
     * reading [open], with no suspension in between: the copies are taken here, so a [lock] that ran
     * before this call would have left [keysFrom] wiped. Anything that suspends first (Argon2id,
     * Keystore) must copy the keys itself beforehand and use [persistWith].
     */
    private suspend fun persist(header: VaultContainer.Header, keysFrom: OpenVault, data: VaultData) {
        val dek = keysFrom.dek.copyOf()
        val layerKey = keysFrom.layerKey.copyOf()
        try {
            persistWith(header, dek, layerKey, data)
        } finally {
            dek.wipe()
            layerKey.wipe()
        }
    }

    /**
     * Encrypts and writes [data] with the given keys, which the caller copied before any suspension
     * and wipes afterwards. `AesGcm` refuses an all-zero key, so a wiped key can never reach disk.
     */
    private suspend fun persistWith(header: VaultContainer.Header, dek: ByteArray, layerKey: ByteArray, data: VaultData) {
        withContext(Dispatchers.IO) {
            val portable = VaultContainer.seal(header, dek, data)
            writeVault(DeviceLayer.seal(layerKey, portable))
        }
    }

    /** Every write of the vault file goes through here, so the integrity record always describes the file on disk. */
    private fun writeVault(bytes: ByteArray) {
        storage.writeVault(bytes)
        integrity.recordWrite(bytes)
    }

    /**
     * Checks the current password, then protects the vault with the new one under a new vault key
     * (DEK): the contents are encrypted again, so an old backup opened with the old password gives
     * no key for the copies made from now on. The fingerprint copy wrapped the old DEK, so it is
     * turned off and the caller asks the user to enable it again. The layer key is copied before
     * Argon2id runs and, if the vault locked meanwhile, nothing is written: the file is never
     * sealed with the wiped keys of a vault that is no longer open.
     */
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): OperationResult =
        writeMutex.withLock {
            try {
                // Same throttle as unlock: with the vault open (e.g. by fingerprint) this dialog
                // would otherwise be an unlimited oracle of the master password.
                val blockedUntil = withContext(Dispatchers.IO) { throttle.blockedUntil() }
                if (blockedUntil > 0) return@withLock OperationResult.Throttled(blockedUntil)
                val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
                val lockCountAtStart = lockCount
                val layerKey = current.layerKey.copyOf()
                var rekeyed: VaultContainer.Opened? = null
                try {
                    rekeyed = withContext(Dispatchers.Default) {
                        if (!VaultContainer.verifyPassword(current.header, currentPassword)) {
                            null
                        } else {
                            VaultContainer.changePassword(newPassword, current.data)
                        }
                    }
                    if (rekeyed == null) {
                        val until = withContext(Dispatchers.IO) { throttle.recordFailure() }
                        return@withLock if (until > 0) OperationResult.Throttled(until) else OperationResult.WrongPassword
                    }
                    withContext(Dispatchers.IO) { throttle.reset() }
                    if (open !== current || lockCount != lockCountAtStart) {
                        return@withLock OperationResult.Failure(
                            "Se bloqueó mientras se cambiaba la contraseña. No se ha escrito nada.",
                        )
                    }
                    // biometric.key wraps the old DEK and would open nothing from now on. Off before
                    // the file changes: if the write fails, the old password still works and the
                    // fingerprint is simply off.
                    withContext(Dispatchers.IO) { biometricKeys.disable() }
                    current.biometricEnabled = false
                    if (open === current) publish(current)
                    persistWith(rekeyed.header, rekeyed.dek, layerKey, current.data)
                    if (open === current) {
                        val updated = OpenVault(
                            rekeyed.header,
                            rekeyed.dek.copyOf(),
                            layerKey.copyOf(),
                            current.data,
                            biometricEnabled = false,
                            otpOnDevice = current.otpOnDevice,
                            integrityWarning = current.integrityWarning,
                            deviceKeySecurityLevel = current.deviceKeySecurityLevel,
                        )
                        current.wipe()
                        open = updated
                        publish(updated)
                    }
                } finally {
                    rekeyed?.let { it.dek.wipe() }
                    layerKey.wipe()
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

    // region 2FA codes

    /**
     * Cipher for the fingerprint prompt that opens the 2FA key, or null if it can't be prepared.
     * When the system invalidated the key (a fingerprint was added, for example), the state
     * switches to [OtpAccess.LOCKED].
     */
    fun otpUnlockCipher(): Cipher? {
        val current = open ?: return null
        val keyring = current.data.otpKeyring ?: return null
        val cipher = try {
            otpKeys.unlockCipher(keyring.id)
        } catch (e: Exception) {
            return null
        }
        if (cipher == null && current.otpOnDevice) {
            current.otpOnDevice = false
            if (open === current) publish(current)
        }
        return cipher
    }

    /** Cipher for the fingerprint prompt that creates this phone's 2FA key (first use or recovery). */
    fun otpEnrollmentCipher(): Cipher? =
        try {
            otpKeys.enrollmentCipher()
        } catch (e: Exception) {
            null
        }

    /**
     * First 2FA code: creates the 2FA key, protects it with the fingerprint key authorized in
     * [authorizedCipher] and with [recoveryCode], and stores [secret] in the entry [entryId].
     * The caller keeps (and later wipes) [recoveryCode] and [secret].
     */
    suspend fun setUpOtp(
        authorizedCipher: Cipher,
        recoveryCode: CharArray,
        entryId: String,
        secret: OtpSecret,
    ): OperationResult = modify(otpOnDeviceAfter = true) { data ->
        if (data.otpKeyring != null) throw IllegalStateException("2FA is already set up")
        val entry = data.entries.find { it.id == entryId } ?: throw IllegalStateException("Entry not found")
        val otpKey = OtpCrypto.newKey()
        try {
            val keyringId = OtpCrypto.newKeyringId()
            val keyring = OtpCrypto.createKeyring(otpKey, keyringId, recoveryCode)
            val sealed = OtpCrypto.seal(otpKey, keyringId, entryId, secret)
            // The phone's copy goes first: a vault that has a keyring but no copy on the phone
            // would ask for the recovery code right away.
            otpKeys.finishEnrollment(authorizedCipher, keyringId, otpKey)
            data.withEntry(entry.copy(otp = sealed, updatedAt = System.currentTimeMillis()))
                .copy(otpKeyring = keyring)
        } finally {
            otpKey.wipe()
        }
    }

    /** Stores [secret] in the entry [entryId]. The caller keeps (and later wipes) [secret]. */
    suspend fun addOtp(authorizedCipher: Cipher, entryId: String, secret: OtpSecret): OperationResult = modify { data ->
        val keyring = data.otpKeyring ?: throw IllegalStateException("2FA is not set up")
        val entry = data.entries.find { it.id == entryId } ?: throw IllegalStateException("Entry not found")
        val otpKey = otpKeys.unwrap(authorizedCipher, keyring.id)
        try {
            val sealed = OtpCrypto.seal(otpKey, keyring.id, entryId, secret)
            data.withEntry(entry.copy(otp = sealed, updatedAt = System.currentTimeMillis()))
        } finally {
            otpKey.wipe()
        }
    }

    /** Deletes the 2FA secret of an entry. Needs no fingerprint: it reveals nothing. */
    suspend fun removeOtp(entryId: String): OperationResult = modify { data ->
        val entry = data.entries.find { it.id == entryId } ?: throw IllegalStateException("Entry not found")
        data.withEntry(entry.copy(otp = null, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Opens the 2FA secret of [entryId] with the fingerprint-authorized [authorizedCipher]. The
     * caller wipes the result as soon as the code is no longer on screen. Null if it failed or the
     * vault locked meanwhile.
     */
    suspend fun revealOtp(authorizedCipher: Cipher, entryId: String): OtpSecret? {
        val current = open ?: return null
        val keyring = current.data.otpKeyring ?: return null
        val sealed = current.data.entries.find { it.id == entryId }?.otp ?: return null
        val lockCountAtStart = lockCount
        val secret = try {
            withContext(Dispatchers.Default) {
                val otpKey = otpKeys.unwrap(authorizedCipher, keyring.id)
                try {
                    OtpCrypto.open(otpKey, keyring.id, entryId, sealed)
                } finally {
                    otpKey.wipe()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AEADBadTagException) {
            // The phone's copy did not belong to this key: the manager deleted it, so the codes
            // now need the recovery code, and the screen must offer it instead of a generic error.
            if (open === current && current.otpOnDevice) {
                current.otpOnDevice = false
                publish(current)
            }
            return null
        } catch (e: Exception) {
            return null
        }
        if (lockCount != lockCountAtStart) {
            secret.wipe()
            return null
        }
        return secret
    }

    /** True if [recoveryCode] opens the 2FA keyring of the vault. Slow: Argon2id. */
    suspend fun checkOtpRecoveryCode(recoveryCode: CharArray): Boolean {
        val keyring = open?.data?.otpKeyring ?: return false
        val code = recoveryCode.copyOf()
        return try {
            withContext(Dispatchers.Default) {
                try {
                    OtpCrypto.unwrapWithRecoveryCode(keyring, code).wipe()
                    true
                } catch (e: WrongRecoveryCodeException) {
                    false
                }
            }
        } finally {
            code.wipe()
        }
    }

    /**
     * Gives this phone a new fingerprint-protected copy of the 2FA key, recovered with
     * [recoveryCode]. [authorizedCipher] comes from [otpEnrollmentCipher]. The caller wipes the code.
     */
    suspend fun recoverOtp(authorizedCipher: Cipher, recoveryCode: CharArray): OperationResult = writeMutex.withLock {
        val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
        val keyring = current.data.otpKeyring
            ?: return@withLock OperationResult.Failure("No hay códigos 2FA que recuperar.")
        val code = recoveryCode.copyOf()
        try {
            withContext(Dispatchers.Default) {
                val otpKey = OtpCrypto.unwrapWithRecoveryCode(keyring, code)
                try {
                    otpKeys.finishEnrollment(authorizedCipher, keyring.id, otpKey)
                } finally {
                    otpKey.wipe()
                }
            }
            current.otpOnDevice = true
            if (open === current) publish(current)
            OperationResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: WrongRecoveryCodeException) {
            OperationResult.WrongPassword
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            code.wipe()
        }
    }

    /**
     * Replaces the recovery code and, with it, the 2FA key: every sealed secret is opened with the
     * current key (fingerprint in [authorizedCipher], from [otpUnlockCipher]) and sealed again
     * under a new random key with a new keyring id. [newCode] wraps the new key inside the vault
     * and the fingerprint key authorized in [enrollmentCipher] (from [otpEnrollmentCipher], asked
     * before calling) wraps this phone's copy. Backups made before keep the old key and the old
     * code, so neither opens the secrets of later copies. The caller wipes [newCode].
     *
     * The vault is written first: if this phone's copy then fails, the codes are recovered with
     * the new code, which the user has just written down.
     */
    suspend fun replaceOtpRecoveryCode(
        authorizedCipher: Cipher,
        enrollmentCipher: Cipher,
        newCode: CharArray,
    ): OperationResult = writeMutex.withLock {
        val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
        val keyring = current.data.otpKeyring ?: return@withLock OperationResult.Failure("No hay códigos 2FA.")
        val header = current.header
        val data = current.data
        val dek = current.dek.copyOf()
        val layerKey = current.layerKey.copyOf()
        val newKey = OtpCrypto.newKey()
        val newId = OtpCrypto.newKeyringId()
        try {
            val newData = withContext(Dispatchers.Default) {
                val oldKey = otpKeys.unwrap(authorizedCipher, keyring.id)
                val entries = try {
                    OtpCrypto.reseal(data.entries, oldKey, keyring.id, newKey, newId)
                } finally {
                    oldKey.wipe()
                }
                val changed = data.copy(entries = entries, otpKeyring = OtpCrypto.createKeyring(newKey, newId, newCode))
                val portable = VaultContainer.seal(header, dek, changed)
                writeVault(DeviceLayer.seal(layerKey, portable))
                changed
            }
            // From here the vault holds the new key, whatever happens to this phone's copy.
            if (open === current) {
                current.data = newData
                current.otpOnDevice = false
            }
            try {
                withContext(Dispatchers.Default) { otpKeys.finishEnrollment(enrollmentCipher, newId, newKey) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (open === current) publish(current)
                return@withLock OperationResult.Failure(
                    "Código cambiado, pero este móvil no pudo guardar la llave nueva. " +
                        "Recupera los códigos 2FA con el código nuevo.",
                )
            }
            if (open === current) {
                current.otpOnDevice = true
                publish(current)
            }
            OperationResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            newKey.wipe()
            dek.wipe()
            layerKey.wipe()
        }
    }

    /**
     * Applies a change that needs slow work (Argon2id, Keystore) and saves it. The keys are copied
     * before any suspension, so a lock in the middle can't make it seal the vault with wiped keys.
     */
    private suspend fun modify(
        otpOnDeviceAfter: Boolean? = null,
        change: (VaultData) -> VaultData,
    ): OperationResult = writeMutex.withLock {
        val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
        val header = current.header
        val data = current.data
        val dek = current.dek.copyOf()
        val layerKey = current.layerKey.copyOf()
        try {
            val newData = withContext(Dispatchers.Default) {
                val changed = change(data)
                val portable = VaultContainer.seal(header, dek, changed)
                writeVault(DeviceLayer.seal(layerKey, portable))
                changed
            }
            if (otpOnDeviceAfter != null) current.otpOnDevice = otpOnDeviceAfter
            if (open === current) {
                current.data = newData
                publish(current)
            }
            OperationResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            dek.wipe()
            layerKey.wipe()
        }
    }

    private fun VaultData.withEntry(entry: VaultEntry): VaultData =
        copy(entries = entries.map { if (it.id == entry.id) entry else it })

    // endregion

    private fun describe(e: Exception): String = when (e) {
        // The key is intact: the Keystore just did not answer. Restoring would be the one thing
        // that destroys the current vault, so it is never suggested here.
        is KeystoreUnavailableException ->
            "El almacén de claves del teléfono no respondió. Vuelve a intentarlo o reinicia el teléfono."
        is DeviceBindingException ->
            "La bóveda no se puede abrir en este teléfono: su clave de hardware no está disponible. " +
                "Restaura una copia de seguridad."
        is CorruptedVaultException -> "El archivo está dañado o ha sido modificado."
        is UnsupportedVaultException -> "Formato de bóveda no compatible."
        is VaultException -> "Error de la bóveda."
        is IOException -> "No se pudo leer o escribir el archivo."
        is GeneralSecurityException, is ProviderException -> "El almacén de claves del sistema rechazó la operación."
        // AesGcm refused a wiped (all-zero) key before sealing anything: the file is untouched.
        is IllegalArgumentException -> "Error interno: no se ha escrito nada"
        else -> "Error inesperado."
    }

    companion object {
        private const val SOFTWARE_KEY_WARNING =
            "La clave de este teléfono es solo de software: una copia de los archivos de la app " +
                "sacada del teléfono podría abrirse en otro sitio con la contraseña maestra."

        fun create(context: Context, scope: CoroutineScope): VaultSession {
            val appContext = context.applicationContext
            val storage = VaultStorage(appContext)
            val keys = KeystoreKeys(appContext)
            return VaultSession(
                storage = storage,
                deviceKeys = DeviceKeyManager(keys, storage.layerKeyFile),
                biometricKeys = BiometricKeyManager(keys, storage.biometricKeyFile),
                otpKeys = OtpKeyManager(keys, storage.otpKeyFile),
                throttle = UnlockThrottle(appContext),
                integrity = VaultIntegrity(appContext),
                clipboard = SecureClipboard(appContext, scope),
                scope = scope,
            )
        }
    }
}
