package io.github.jls97.boveda.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jls97.boveda.core.generator.PasswordStrength
import io.github.jls97.boveda.session.OperationResult
import io.github.jls97.boveda.session.VaultSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.crypto.Cipher

data class LockUiState(
    val busy: Boolean = false,
    val error: String? = null,
    /** Epoch millis until which unlocking is blocked after too many wrong passwords. */
    val blockedUntil: Long = 0L,
)

/** Drives the screens shown while the vault is closed: first-run setup and unlock. */
class LockViewModel(private val session: VaultSession) : ViewModel() {
    private val _ui = MutableStateFlow(LockUiState())
    val ui: StateFlow<LockUiState> = _ui.asStateFlow()

    fun createVault(password: String, confirmation: String) {
        if (_ui.value.busy) return
        val problem = masterPasswordProblem(password, confirmation)
        if (problem != null) {
            showError(problem)
            return
        }
        launchOperation { session.create(password.toCharArray()) }
    }

    fun unlock(password: String) {
        if (_ui.value.busy || password.isEmpty()) return
        launchOperation { session.unlock(password.toCharArray()) }
    }

    fun isBiometricEnabled(): Boolean = session.isBiometricEnabled()

    fun biometricCipher(): Cipher? = session.biometricUnlockCipher()

    fun unlockWithBiometric(authorizedCipher: Cipher) {
        launchOperation { session.unlockWithBiometric(authorizedCipher) }
    }

    fun restoreBackup(backup: ByteArray, password: String) {
        if (_ui.value.busy) return
        launchOperation { session.restoreBackup(backup, password.toCharArray()) }
    }

    fun expectExternalActivity() = session.expectExternalActivity()

    fun showError(message: String) {
        _ui.value = _ui.value.copy(busy = false, error = message)
    }

    private fun launchOperation(operation: suspend () -> OperationResult) {
        _ui.value = LockUiState(busy = true)
        viewModelScope.launch {
            _ui.value = when (val result = operation()) {
                OperationResult.Success -> LockUiState()
                is OperationResult.Restored -> LockUiState()
                OperationResult.WrongPassword -> LockUiState(error = "Contraseña incorrecta.")
                OperationResult.WrongCurrentPassword -> LockUiState(error = "La contraseña maestra actual no es correcta.")
                is OperationResult.Throttled -> LockUiState(
                    error = "Demasiados intentos fallidos.",
                    blockedUntil = result.untilMillis,
                )
                is OperationResult.Failure -> LockUiState(error = result.message)
            }
        }
    }

    companion object {
        /** Why [password] can't be a master password, or null if it is fine. */
        fun masterPasswordProblem(password: String, confirmation: String): String? = when {
            password.length < PasswordStrength.MASTER_MIN_LENGTH ->
                "Usa al menos ${PasswordStrength.MASTER_MIN_LENGTH} caracteres."
            !PasswordStrength.isAcceptableMasterPassword(password) ->
                "Es demasiado predecible. Prueba con una frase de varias palabras, números y símbolos."
            password != confirmation -> "Las contraseñas no coinciden."
            else -> null
        }
    }
}
