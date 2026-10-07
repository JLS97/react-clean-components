package io.github.jls97.boveda.session

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.crypto.wipe
import io.github.jls97.boveda.core.otp.OtpCrypto
import io.github.jls97.boveda.core.otp.OtpSecret
import io.github.jls97.boveda.core.otp.WrongRecoveryCodeException
import io.github.jls97.boveda.core.vault.CorruptedVaultException
import io.github.jls97.boveda.core.vault.DeviceBindingException
import io.github.jls97.boveda.core.vault.DeviceLayer
import io.github.jls97.boveda.core.vault.FieldTooLongException
import io.github.jls97.boveda.core.vault.KdfMemoryException
import io.github.jls97.boveda.core.vault.OversizedFieldException
import io.github.jls97.boveda.core.vault.UnsupportedVaultException
import io.github.jls97.boveda.core.vault.VaultContainer
import io.github.jls97.boveda.core.vault.VaultData
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.core.vault.VaultException
import io.github.jls97.boveda.core.vault.VaultSettings
import io.github.jls97.boveda.core.vault.WrongPasswordException
import io.github.jls97.boveda.data.VaultFiles
import io.github.jls97.boveda.data.VaultStorage
import io.github.jls97.boveda.security.BiometricKeyManager
import io.github.jls97.boveda.security.DeviceKeyManager
import io.github.jls97.boveda.security.FingerprintKeys
import io.github.jls97.boveda.security.KeySecurityLevel
import io.github.jls97.boveda.security.KeystoreKeys
import io.github.jls97.boveda.security.KeystoreUnavailableException
import io.github.jls97.boveda.security.LayerKeys
import io.github.jls97.boveda.security.OtpKeyManager
import io.github.jls97.boveda.security.OtpKeys
import io.github.jls97.boveda.security.SecureClipboard
import io.github.jls97.boveda.security.UnlockThrottle
import io.github.jls97.boveda.security.VaultClipboard
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
        /** Argon2id costs of the header the vault is written with. For the settings screen. */
        val kdfParams: KdfParams = KdfParams.DEFAULT,
        /**
         * Set when the vault opened with costs below the app's default and could not be written
         * again with the default ones ([KdfUpgradePolicy]): it keeps opening, with the old costs.
         */
        val kdfUpgradeWarning: String? = null,
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

    /**
     * A backup was restored ([VaultSession.restoreBackup]). [masterPasswordChanged] is set when a
     * vault was replaced and its master password is not known to be the backup's: the UI must say
     * that the master password is now the backup's. [hadUndo] is set when the replaced vault was
     * kept for [VaultSession.undoRestore].
     */
    data class Restored(val masterPasswordChanged: Boolean, val hadUndo: Boolean) : OperationResult

    data object WrongPassword : OperationResult

    /** The master password of the vault already on this phone (asked besides the backup's) is wrong. */
    data object WrongCurrentPassword : OperationResult

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
 *
 * El constructor recibe los archivos, las claves y el portapapeles como interfaces y el reloj
 * monótono como función, para que los tests JVM construyan una sesión con dobles en memoria
 * (`VaultSessionTest`) y ejerciten las carreras con [lock]; la app la crea con [create].
 */
class VaultSession internal constructor(
    private val storage: VaultFiles,
    private val deviceKeys: LayerKeys,
    private val biometricKeys: FingerprintKeys,
    private val otpKeys: OtpKeys,
    private val throttle: UnlockThrottle,
    private val integrity: VaultIntegrity,
    val clipboard: VaultClipboard,
    private val scope: CoroutineScope,
    /** Whether the phone has a secure lock screen (PIN, pattern, password) right now (B-43). */
    private val deviceSecure: () -> Boolean,
    /** Milisegundos desde el arranque ([SystemClock.elapsedRealtime] en la app). */
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
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
        /** [header] has costs below [KdfParams.DEFAULT] and the rewrite on unlock failed. */
        val kdfUpgradeFailed: Boolean = false,
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
    private var lastInteraction = elapsedRealtime()
    private var autoLockJob: Job? = null

    /** Monotonic deadline of the last [expectExternalActivity]; 0 when none. */
    private var externalActivityExpectedUntil = 0L

    /** Monotonic instant of the last [onAppBackground] not yet followed by [onAppForeground]. */
    private var backgroundSince: Long? = null

    // region Lifecycle and auto-lock

    /** Call on every user interaction; it postpones the inactivity lock. */
    fun touch() {
        lastInteraction = elapsedRealtime()
    }

    /**
     * Call right before opening a system screen on purpose (the file picker for backups, the
     * autofill settings), so "lock when leaving the app" does not lock in the middle of the
     * operation. The exception lasts [AutoLockPolicy.EXTERNAL_ACTIVITY_GRACE_MS] at most: when
     * it expires with the app still in the background, the auto-lock timer locks right then
     * ([AutoLockPolicy.shouldLockOnAnnouncementExpiry]), so a picker abandoned with the Home
     * button cannot leave the vault open without limit. Coming back to the front, as the picker
     * does when it returns, clears the announcement. The screen-off lock still applies.
     */
    fun expectExternalActivity() {
        externalActivityExpectedUntil = AutoLockPolicy.externalActivityDeadline(elapsedRealtime())
    }

    private fun isExternalActivityExpected(): Boolean =
        AutoLockPolicy.isExternalActivityExpected(externalActivityExpectedUntil, elapsedRealtime())

    fun onAppForeground() {
        externalActivityExpectedUntil = 0L
        val since = backgroundSince
        backgroundSince = null
        val current = open ?: return
        val shouldLock = AutoLockPolicy.shouldLockOnForeground(
            current.data.settings.autoLockSeconds,
            since,
            lastInteraction,
            elapsedRealtime(),
        )
        if (shouldLock) lock()
    }

    fun onAppResumed() {
        _resumeTicks.value++
    }

    fun onAppBackground() {
        backgroundSince = elapsedRealtime()
        val current = open ?: return
        if (AutoLockPolicy.shouldLockOnBackground(current.data.settings.autoLockSeconds, isExternalActivityExpected())) lock()
    }

    /**
     * Whether fingerprint unlock is set up. Only a hint for the unlock screen, which calls it while
     * composing: a Keystore that does not answer (busy StrongBox, daemon restarting) must not crash
     * the app there, so it reads as "off" and the password field shows; the fingerprint comes back
     * on the next unlock screen once the Keystore answers.
     */
    fun isBiometricEnabled(): Boolean =
        try {
            biometricKeys.isEnabled()
        } catch (e: GeneralSecurityException) {
            false
        } catch (e: ProviderException) {
            false
        }

    /** Forgets every key and decrypted value. Safe to call at any time and more than once. */
    fun lock() {
        lockCount++
        autoLockJob?.cancel()
        autoLockJob = null
        // An announcement made before locking must not survive into the next unlock.
        externalActivityExpectedUntil = 0L
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
                // Against the monotonic clock, not the count of ticks: a frozen process (cached
                // apps freezer, doze) misses ticks but not the time that went by.
                val now = elapsedRealtime()
                val autoLockSeconds = current.data.settings.autoLockSeconds
                val shouldLock = AutoLockPolicy.shouldLockForInactivity(autoLockSeconds, lastInteraction, now) ||
                    AutoLockPolicy.shouldLockOnAnnouncementExpiry(autoLockSeconds, externalActivityExpectedUntil, backgroundSince, now)
                if (shouldLock) {
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
            kdfParams = current.header.kdfParams,
            kdfUpgradeWarning = KDF_UPGRADE_WARNING.takeIf { current.kdfUpgradeFailed },
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

    /**
     * Creates a new vault protected by [password]. The array is wiped. Refused without a secure
     * lock screen: the layer key only protects while the phone is locked (B-43).
     */
    suspend fun create(password: CharArray): OperationResult = writeMutex.withLock {
        try {
            if (!deviceSecure()) return@withLock OperationResult.Failure(SECURE_LOCK_SCREEN_REQUIRED)
            val lockCountAtStart = lockCount
            val newVault = withContext(Dispatchers.Default) {
                // A key created now would not open the copy kept for undoRestore, if any.
                val layerKey = deviceKeys.loadOrCreate(beforeCreate = storage::deletePreviousVault)
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

    /**
     * Unlocks with the master password. The array is wiped.
     *
     * A vault whose header has cheaper Argon2id costs than [KdfParams.DEFAULT] is written again
     * with the default ones before it is published ([KdfUpgradePolicy]): the password is at
     * hand and the DEK does not change, so the fingerprint copy keeps working. If that write
     * fails the unlock goes on with the old header and [VaultState.Unlocked.kdfUpgradeWarning]
     * says so. The fingerprint unlock has no password, so it never does this.
     */
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
                } catch (e: OutOfMemoryError) {
                    // Argon2id ran out of heap despite the check: report it instead of dying.
                    throw KdfMemoryException("Key derivation ran out of memory")
                }
            }
            if (newVault == null) {
                val until = withContext(Dispatchers.IO) { throttle.recordFailure() }
                return@withLock if (until > 0) OperationResult.Throttled(until) else OperationResult.WrongPassword
            }
            withContext(Dispatchers.IO) { throttle.reset() }
            // Only while nobody locked meanwhile: finishUnlock would discard the vault anyway.
            val upgraded = if (KdfUpgradePolicy.shouldUpgrade(newVault.header.kdfParams) && lockCount == lockCountAtStart) {
                withContext(Dispatchers.Default) { upgradeKdf(newVault, password) }
            } else {
                newVault
            }
            finishUnlock(upgraded, lockCountAtStart)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            password.wipe()
        }
    }

    /**
     * Rebuilds the header of [newVault], a vault just opened and not yet published, with
     * [KdfParams.DEFAULT] and writes the file. [newVault] is private to the unlock in progress
     * (not [open]), so a [lock] meanwhile cannot wipe its keys under this; the caller still
     * checks the lock count before publishing. Returns the vault with the new header or, if
     * anything fails, the same vault flagged with [OpenVault.kdfUpgradeFailed]: the file on disk
     * is written atomically, so it still holds the old header that [newVault] describes.
     * Runs off the main thread: Argon2id.
     */
    private fun upgradeKdf(newVault: OpenVault, password: CharArray): OpenVault {
        val header = try {
            val upgraded = VaultContainer.upgradeKdf(password, newVault.dek)
            val portable = VaultContainer.seal(upgraded, newVault.dek, newVault.data)
            writeVault(DeviceLayer.seal(newVault.layerKey, portable))
            upgraded
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            // Argon2id with the default costs ran out of heap: the old costs still open the vault.
            null
        }
        val result = OpenVault(
            header ?: newVault.header,
            newVault.dek.copyOf(),
            newVault.layerKey.copyOf(),
            newVault.data,
            biometricEnabled = newVault.biometricEnabled,
            otpOnDevice = newVault.otpOnDevice,
            integrityWarning = newVault.integrityWarning,
            deviceKeySecurityLevel = newVault.deviceKeySecurityLevel,
            kdfUpgradeFailed = header == null,
        )
        newVault.wipe()
        return result
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
     * Replaces the vault on this phone with a backup. Works locked or unlocked. The vault.bin being
     * replaced is kept as vault.prev.bin, sealed with the same layer key, so [undoRestore] can put
     * it back until the next restore; the layer key is never rotated while the current vault is
     * readable ([DeviceKeyManager.loadOrCreate]). Both arrays are wiped.
     *
     * Whether [currentPassword] is needed is decided by [RestorePolicy]. With a vault on this phone:
     * - unlocked, it is required and checked against the open vault's header;
     * - locked, it is checked if given (against the header of the file on disk) and may be omitted
     *   only with [forceWithoutCurrent], the "I do not remember my password" path that the UI
     *   enables after a strong confirmation (a typed word, a delay): it writes over a vault nobody
     *   proved to own, so the wrong-password throttle is not cleared and the kept copy is the only
     *   way back. A vault on disk that this phone can no longer open (permanent Keystore failure,
     *   damaged file) has nothing left to protect, so the check is skipped for it; a Keystore that
     *   merely did not answer fails the restore so that it can be retried.
     * Without a vault on this phone nothing is checked. The throttle covers both checks: each
     * password is an oracle of a master password.
     *
     * biometric.key wraps the DEK of the vault that leaves, so it is deleted. otp.key is kept when
     * it holds the 2FA key of the restored vault (an older backup of this same vault keeps working
     * with the fingerprint) and also while the vault that leaves can come back with [undoRestore]:
     * the key may be that vault's, and without it the codes would need the recovery code after an
     * undo. It is of no use to the restored vault meanwhile (the keyring id does not match). It is
     * deleted when there is no undo, when the open vault that leaves is known not to own it, and
     * in [discardUndo]. Not covered: turning on or recovering 2FA in the restored vault writes a
     * new otp.key, so after an undo the vault that comes back is in [OtpAccess.LOCKED] (a
     * deliberate action, not a loss).
     *
     * Restoring again the very vault already on the phone (a restore that finished while the
     * phone locked, or the same backup chosen twice) leaves vault.prev.bin alone, so the vault
     * that [undoRestore] brings back is the one from before the first restore, not the open one.
     * Everything tied to that copy is then left as it is too: the wrong-password count kept aside
     * by a forced restore (a password proved for the restored vault clears only the live count)
     * and otp.key, even when the open vault is known not to own it.
     *
     * On success the result is [OperationResult.Restored], which says whether the master password
     * is now a different one (the backup's) and whether the copy for [undoRestore] exists. It is
     * returned even if the phone locked while the file was being written: the restore is done and
     * the state is then [VaultState.Locked], so retrying would only write the restored copy over
     * the vault kept for the undo.
     *
     * A backup whose header has cheaper Argon2id costs than the app's default is written with
     * the default ones, by the same rule as [unlock] ([KdfUpgradePolicy]) and the same mechanics
     * ([VaultContainer.upgradeKdf]): the backup's DEK is kept and only the header changes. The
     * DEK is not rotated on purpose: the user keeps the file it came from, so a new DEK would
     * add nothing the backup does not already give away, and the one rule decides both paths.
     *
     * Refused without a secure lock screen, as [create] is (B-43).
     */
    suspend fun restoreBackup(
        backup: ByteArray,
        password: CharArray,
        currentPassword: CharArray?,
        forceWithoutCurrent: Boolean,
    ): OperationResult = writeMutex.withLock {
        try {
            if (!deviceSecure()) return@withLock OperationResult.Failure(SECURE_LOCK_SCREEN_REQUIRED)
            // Same throttle as unlock: checking the backup's password is also a password oracle.
            val blockedUntil = withContext(Dispatchers.IO) { throttle.blockedUntil() }
            if (blockedUntil > 0) return@withLock OperationResult.Throttled(blockedUntil)
            val current = open
            val vaultOnPhone = current != null || withContext(Dispatchers.IO) { storage.vaultExists() }
            val decision = RestorePolicy.decide(
                vaultOnPhone = vaultOnPhone,
                unlocked = current != null,
                currentPasswordGiven = currentPassword != null,
                forceWithoutCurrent = forceWithoutCurrent,
            )
            if (decision == RestorePolicy.Decision.REFUSE) {
                return@withLock OperationResult.Failure(
                    if (current != null) CURRENT_PASSWORD_REQUIRED else CURRENT_PASSWORD_OR_CONFIRMATION_REQUIRED,
                )
            }
            val masterPasswordChanged = RestorePolicy.masterPasswordChanges(
                vaultOnPhone = vaultOnPhone,
                currentPasswordGiven = currentPassword != null,
                samePassword = currentPassword?.contentEquals(password) == true,
            )
            if (decision == RestorePolicy.Decision.VERIFY_CURRENT && currentPassword != null) {
                val currentHeader = current?.header
                val currentPasswordOk = withContext(Dispatchers.Default) {
                    try {
                        verifyCurrentPassword(currentHeader, currentPassword)
                    } catch (e: OutOfMemoryError) {
                        throw KdfMemoryException("Key derivation ran out of memory")
                    }
                }
                if (!currentPasswordOk) {
                    val until = withContext(Dispatchers.IO) { throttle.recordFailure() }
                    return@withLock if (until > 0) OperationResult.Throttled(until) else OperationResult.WrongCurrentPassword
                }
            }
            val lockCountAtStart = lockCount
            var hadUndo = false
            val newVault = withContext(Dispatchers.Default) {
                val opened = try {
                    VaultContainer.open(backup, password)
                } catch (e: WrongPasswordException) {
                    null
                } catch (e: OutOfMemoryError) {
                    // Argon2id ran out of heap despite the check: report it instead of dying.
                    throw KdfMemoryException("Key derivation ran out of memory")
                } ?: return@withContext null
                // A backup with cheaper Argon2 costs than the app's own would otherwise turn this
                // vault, and every backup made from it, into an easier offline target for good.
                // Same rule and same mechanics as unlock: the password is at hand, so the DEK is
                // wrapped again under the default costs and nothing else changes.
                val restored = if (KdfUpgradePolicy.shouldUpgrade(opened.header.kdfParams)) {
                    try {
                        VaultContainer.Opened(VaultContainer.upgradeKdf(password, opened.dek), opened.dek, opened.data)
                    } catch (e: Throwable) {
                        opened.dek.wipe()
                        throw e
                    }
                } else {
                    opened
                }
                try {
                    // Only created when nothing opens with the old key, in which case the copy for
                    // undoRestore would be unreadable too.
                    var layerKeyRotated = false
                    val layerKey = deviceKeys.loadOrCreate(beforeCreate = { layerKeyRotated = true })
                    try {
                        val portable = VaultContainer.seal(restored.header, restored.dek, restored.data)
                        val sealed = DeviceLayer.seal(layerKey, portable)
                        val kept = keepPreviousVault(keep = vaultOnPhone && !layerKeyRotated, layerKey, restored)
                        hadUndo = kept != KeptPrevious.NONE
                        writeVault(sealed)
                        biometricKeys.disable()
                        // An older backup of this same vault keeps working with the fingerprint.
                        // Otherwise the key lives as long as the vault it may belong to can come
                        // back: with no undo, or when the open vault that leaves (and is the one
                        // the undo brings back) does not own it, it is an orphan. When the copy
                        // kept is an older vault, the key may be that one's: it stays.
                        val otpOnDevice = otpOnDevice(restored.data)
                        val otpOrphan = when (kept) {
                            KeptPrevious.NONE -> true
                            KeptPrevious.REPLACED -> current?.otpOnDevice == false
                            KeptPrevious.PRESERVED -> false
                        }
                        if (!otpOnDevice && otpOrphan) otpKeys.disable()
                        // A forced restore proved nothing about the owner: the count stays and is
                        // kept aside, so an unlock of the restored copy cannot clear it for the
                        // vault that comes back with undoRestore. The slot belongs to the vault in
                        // vault.prev.bin: it is only written or emptied when that copy changes,
                        // never when an older vault stays there (R01-9).
                        val resetsThrottle = RestorePolicy.resetsThrottle(decision)
                        if (resetsThrottle) throttle.reset()
                        when (kept) {
                            KeptPrevious.PRESERVED -> Unit
                            KeptPrevious.REPLACED -> if (resetsThrottle) throttle.discardKept() else throttle.keepForUndo()
                            KeptPrevious.NONE -> throttle.discardKept()
                        }
                        OpenVault(
                            restored.header,
                            restored.dek,
                            layerKey,
                            restored.data,
                            biometricEnabled = false,
                            otpOnDevice = otpOnDevice,
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
            // The file is already replaced: if the phone locked meanwhile, finishUnlock leaves the
            // state Locked and the restored vault opens with the backup's password. A restore all
            // the same, so the caller is not told to retry (R01-5).
            finishUnlock(newVault, lockCountAtStart)
            OperationResult.Restored(masterPasswordChanged, hadUndo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            password.wipe()
            currentPassword?.wipe()
        }
    }

    /**
     * [restoreBackup] for the first run, without the current password and without forcing: it
     * only succeeds when there is no vault on this phone yet and refuses otherwise. With a vault
     * on the phone the UI must ask for its master password, or force explicitly while locked.
     */
    suspend fun restoreBackupFirstRun(backup: ByteArray, password: CharArray): OperationResult =
        restoreBackup(backup, password, currentPassword = null, forceWithoutCurrent = false)

    /**
     * True if [password] is the master password of the vault on this phone: the open one
     * ([header] given) or, locked, the file on disk. A file this phone can no longer open for
     * good (device key gone, damaged file) has nothing to protect, so it counts as verified.
     * Runs off the main thread: Argon2id.
     */
    private fun verifyCurrentPassword(header: VaultContainer.Header?, password: CharArray): Boolean {
        if (header != null) return VaultContainer.verifyPassword(header, password)
        val layerKey = try {
            deviceKeys.load()
        } catch (e: DeviceBindingException) {
            null
        } ?: return true
        try {
            val portable = try {
                DeviceLayer.open(layerKey, storage.readVault())
            } catch (e: DeviceBindingException) {
                return true
            } catch (e: CorruptedVaultException) {
                return true
            }
            try {
                val storedHeader = try {
                    VaultContainer.parseHeader(portable)
                } catch (e: CorruptedVaultException) {
                    return true
                } catch (e: UnsupportedVaultException) {
                    return true
                }
                return VaultContainer.verifyPassword(storedHeader, password)
            } finally {
                portable.wipe()
            }
        } finally {
            layerKey.wipe()
        }
    }

    /**
     * What [keepPreviousVault] did with vault.prev.bin: nothing to keep ([NONE]), the vault.bin
     * being replaced is now the copy for [undoRestore] ([REPLACED]), or the copy kept before was
     * left alone because vault.bin already was the vault being restored ([PRESERVED]). Only with
     * [REPLACED] does the undo bring back the vault that was on the phone at the start of the
     * restore; with [PRESERVED] it brings back an older one, which the throttle state kept aside
     * and this phone's otp.key may belong to.
     */
    private enum class KeptPrevious { NONE, REPLACED, PRESERVED }

    /**
     * Keeps the vault.bin about to be replaced as vault.prev.bin for [undoRestore], or drops a
     * stale copy when there is nothing to keep. When vault.bin already holds the very vault being
     * restored ([sameVault]: a restore that finished while the phone locked, done again) the copy
     * kept is left alone: writing the restored vault over it would lose the only copy of the vault
     * that left. Runs off the main thread.
     */
    private fun keepPreviousVault(keep: Boolean, layerKey: ByteArray, restored: VaultContainer.Opened): KeptPrevious {
        if (!keep || !storage.vaultExists()) {
            storage.deletePreviousVault()
            return KeptPrevious.NONE
        }
        val current = storage.readVault()
        if (storage.previousVaultExists() && sameVault(layerKey, current, restored)) return KeptPrevious.PRESERVED
        storage.writePreviousVault(current)
        return KeptPrevious.REPLACED
    }

    /**
     * True if [sealed], a vault file of this phone, holds exactly [restored]: same header (so the
     * same vault key) and same contents. Only authenticated decryption, no Argon2id. Anything that
     * does not open counts as a different vault.
     */
    private fun sameVault(layerKey: ByteArray, sealed: ByteArray, restored: VaultContainer.Opened): Boolean =
        try {
            val portable = DeviceLayer.open(layerKey, sealed)
            try {
                val header = VaultContainer.parseHeader(portable)
                header.encoded.contentEquals(restored.header.encoded) &&
                    VaultContainer.openWithKey(portable, restored.dek).let { opened ->
                        opened.dek.wipe()
                        opened.data == restored.data
                    }
            } finally {
                portable.wipe()
            }
        } catch (e: Exception) {
            false
        }

    /** True while the vault.bin that the last [restoreBackup] replaced is still kept. */
    fun canUndoRestore(): Boolean = storage.previousVaultExists()

    /**
     * Puts back the vault that the last [restoreBackup] replaced and keeps the restored one in its
     * place, so calling it again undoes the undo: nothing is lost either way. The session locks
     * (the keys in memory belong to the file that leaves) and the fingerprint is turned off
     * (biometric.key wraps that file's DEK); the vault then opens with its own master password.
     * This phone's 2FA key, if any, stays: the vault compares its id when it opens, so a key kept
     * through the restore opens the codes of the vault that comes back. Only if 2FA was turned on
     * or recovered in the restored vault meanwhile was that key replaced, and the vault that comes
     * back then needs its recovery code ([OtpAccess.LOCKED]). The wrong-password count that a
     * forced restore kept aside comes back with the vault it protects.
     */
    suspend fun undoRestore(): OperationResult = writeMutex.withLock {
        try {
            if (!storage.previousVaultExists()) {
                return@withLock OperationResult.Failure("No hay ninguna restauración que deshacer.")
            }
            lock()
            withContext(Dispatchers.IO) {
                val previous = storage.readPreviousVault()
                val replaced = if (storage.vaultExists()) storage.readVault() else null
                biometricKeys.disable()
                writeVault(previous)
                if (replaced != null) storage.writePreviousVault(replaced) else storage.deletePreviousVault()
                throttle.restoreKept()
            }
            OperationResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            if (open == null) _state.value = if (storage.vaultExists()) VaultState.Locked else VaultState.NoVault
        }
    }

    /**
     * Drops the copy kept for [undoRestore], when the user asks for it, with what only that copy
     * could use: the wrong-password count kept aside by a forced restore and, when the open vault
     * is known not to own it, this phone's 2FA key.
     */
    fun discardUndo() {
        storage.deletePreviousVault()
        throttle.discardKept()
        if (open?.otpOnDevice == false) otpKeys.disable()
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
                val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
                val lockCountAtStart = lockCount
                val layerKey = current.layerKey.copyOf()
                var rekeyed: VaultContainer.Opened? = null
                try {
                    // Same throttle as unlock: with the vault open (e.g. by fingerprint) this dialog
                    // would otherwise be an unlimited oracle of the master password.
                    val check = throttle.checkPassword {
                        withContext(Dispatchers.Default) { VaultContainer.verifyPassword(current.header, currentPassword) }
                    }
                    if (check != OperationResult.Success) return@withLock check
                    rekeyed = withContext(Dispatchers.Default) { VaultContainer.changePassword(newPassword, current.data) }
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

    // region Re-autenticación

    /**
     * Comprueba que [password] es la contraseña maestra de la bóveda abierta, sin desbloquear ni
     * cambiar nada: sirve para volver a pedirla antes de una operación sensible (activar la huella,
     * exportar una copia, relajar un ajuste, deshacer una restauración). Lenta: Argon2id. El array
     * recibido se borra.
     *
     * Pasa por el mismo freno de intentos que el desbloqueo y el cambio de contraseña (A-03, B-30,
     * R02-1): con la bóveda abierta por huella estos diálogos serían, si no, un oráculo ilimitado
     * de la contraseña maestra. Devuelve [OperationResult.Success] si es correcta,
     * [OperationResult.WrongPassword] si no, [OperationResult.Throttled] mientras el freno manda
     * esperar y [OperationResult.Failure] si la bóveda está bloqueada.
     */
    suspend fun verifyMasterPassword(password: CharArray): OperationResult {
        val header = open?.header
        val copy = password.copyOf()
        password.wipe()
        return try {
            if (header == null) {
                OperationResult.Failure("La bóveda está bloqueada")
            } else {
                throttle.checkPassword { withContext(Dispatchers.Default) { VaultContainer.verifyPassword(header, copy) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))
        } finally {
            copy.wipe()
        }
    }

    /**
     * Comprueba que [bytes], releídos del archivo recién escrito, son una copia válida de ESTA
     * bóveda (M-10): la cabecera se analiza con [VaultContainer.parseHeader] y debe ser la misma
     * que la de la bóveda abierta, y el cuerpo se autentica (AES-GCM) con una copia de la DEK. No
     * deriva la contraseña, así que es rápido, y no cambia nada. Devuelve false si la bóveda está
     * bloqueada o la copia no se abre. Lo descifrado se descarta en el acto.
     */
    suspend fun verifyExportedBackup(bytes: ByteArray): Boolean {
        val current = open ?: return false
        val dek = current.dek.copyOf()
        return try {
            withContext(Dispatchers.Default) {
                val header = VaultContainer.parseHeader(bytes)
                if (!header.encoded.contentEquals(current.header.encoded)) return@withContext false
                VaultContainer.openWithKey(bytes, dek).dek.wipe()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        } finally {
            dek.wipe()
        }
    }

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
        is KdfMemoryException ->
            "Este archivo pide más memoria de la que permite el teléfono para comprobar la contraseña. " +
                "No se ha escrito nada."
        // Well formed, just above a ceiling of this version: the file is not accused of damage.
        is OversizedFieldException -> "Una entrada tiene un campo demasiado grande para esta versión de Contraseñora."
        is UnsupportedVaultException -> "Formato de bóveda no compatible o archivo modificado."
        is VaultException -> "Error de la bóveda."
        is IOException -> "No se pudo leer o escribir el archivo."
        is GeneralSecurityException, is ProviderException -> "El almacén de claves del sistema rechazó la operación."
        // The codec refused a field above the format ceiling before sealing anything.
        is FieldTooLongException -> "Un campo de la entrada es demasiado largo para guardarlo. No se ha escrito nada."
        // AesGcm refused a wiped (all-zero) key before sealing anything: the file is untouched.
        is IllegalArgumentException -> "Error interno: no se ha escrito nada"
        else -> "Error inesperado."
    }

    companion object {
        private const val CURRENT_PASSWORD_REQUIRED =
            "Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual."
        private const val CURRENT_PASSWORD_OR_CONFIRMATION_REQUIRED =
            "Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual, " +
                "o confirmar expresamente que se restaura sin ella."
        const val SECURE_LOCK_SCREEN_REQUIRED =
            "Activa antes un bloqueo de pantalla (PIN, patrón o contraseña) en los ajustes del " +
                "teléfono: la clave de hardware de la bóveda depende de él."
        private const val SOFTWARE_KEY_WARNING =
            "La clave de este teléfono es solo de software: una copia de los archivos de la app " +
                "sacada del teléfono podría abrirse en otro sitio con la contraseña maestra."
        private const val KDF_UPGRADE_WARNING =
            "La bóveda usa una derivación de clave más débil que la actual y no se pudo actualizar " +
                "al abrirla. Se volverá a intentar en el próximo desbloqueo con contraseña."

        /**
         * The session of the app. [deviceSecure] says whether the phone has a secure lock screen;
         * injected so that an instrumented test can run on a device without one.
         */
        fun create(
            context: Context,
            scope: CoroutineScope,
            deviceSecure: () -> Boolean = {
                context.applicationContext.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
            },
        ): VaultSession {
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
                deviceSecure = deviceSecure,
            )
        }
    }
}
