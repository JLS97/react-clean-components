package io.github.jls97.boveda.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jls97.boveda.core.generator.PasswordStrength
import io.github.jls97.boveda.data.AntiPhishingPhrase
import io.github.jls97.boveda.data.antiPhishingPhraseProblem
import io.github.jls97.boveda.session.OperationResult
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
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
    /** Algo que salió bien y la pantalla debe contar (una restauración deshecha, por ejemplo). */
    val notice: String? = null,
)

/** Drives the screens shown while the vault is closed: first-run setup and unlock. */
class LockViewModel(private val session: VaultSession) : ViewModel() {
    private val _ui = MutableStateFlow(LockUiState())
    val ui: StateFlow<LockUiState> = _ui.asStateFlow()

    /** Cambia cada vez que la pantalla vuelve al frente: sirve para releer el estado del teléfono. */
    val resumeTicks: StateFlow<Int> = session.resumeTicks

    /**
     * Crea la bóveda y, solo si sale bien, guarda [phrase] en [phrases] como frase antiphishing de la
     * pantalla de desbloqueo (M-04). La frase se comprueba antes de cifrar nada.
     */
    fun createVault(password: String, confirmation: String, phrase: String, phrases: AntiPhishingPhrase) {
        if (_ui.value.busy) return
        val problem = masterPasswordProblem(password, confirmation)
            ?: antiPhishingPhraseProblem(phrase)?.let { "Frase antiphishing: $it" }
        if (problem != null) {
            showError(problem)
            return
        }
        launchOperation {
            session.create(password.toCharArray()).also { result ->
                if (result == OperationResult.Success) phrases.save(phrase)
            }
        }
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

    /** Restaura una copia cuando aún no hay bóveda en el teléfono (pantalla de creación). */
    fun restoreBackupFirstRun(backup: ByteArray, password: String) {
        if (_ui.value.busy) return
        launchOperation { session.restoreBackupFirstRun(backup, password.toCharArray()) }
    }

    /**
     * Restaura una copia con la bóveda bloqueada. Hace falta [currentPassword], la contraseña
     * maestra de la bóveda que hay en el teléfono, o bien [forceWithoutCurrent] tras la
     * confirmación fuerte de la pantalla («no recuerdo la contraseña actual»): entonces no se
     * limpia el freno de intentos y la bóveda sustituida solo vuelve con «deshacer» (B-31).
     */
    fun restoreBackup(backup: ByteArray, password: String, currentPassword: String?, forceWithoutCurrent: Boolean) {
        if (_ui.value.busy) return
        launchOperation {
            session.restoreBackup(backup, password.toCharArray(), currentPassword?.toCharArray(), forceWithoutCurrent)
        }
    }

    /** True mientras se conserva la bóveda que sustituyó la última restauración. */
    fun canUndoRestore(): Boolean = session.canUndoRestore()

    /** Vuelve a la bóveda anterior a la última restauración; queda bloqueada y sin huella. */
    fun undoRestore() {
        if (_ui.value.busy) return
        launchOperation(successNotice = RESTORE_UNDONE) { session.undoRestore() }
    }

    fun expectExternalActivity() = session.expectExternalActivity()

    fun showError(message: String) {
        _ui.value = _ui.value.copy(busy = false, error = message)
    }

    /** Un aviso (no un error) para la pantalla de bloqueo, p. ej. desde Ajustes al deshacer una restauración. */
    fun showNotice(message: String) {
        _ui.value = _ui.value.copy(busy = false, notice = message)
    }

    private fun launchOperation(successNotice: String? = null, operation: suspend () -> OperationResult) {
        _ui.value = LockUiState(busy = true)
        viewModelScope.launch {
            _ui.value = when (val result = operation()) {
                OperationResult.Success -> LockUiState(notice = successNotice)
                // La pantalla desaparece al desbloquearse; el aviso solo importa si la bóveda se
                // bloqueó mientras se escribía la copia y sigue aquí (R01-5).
                is OperationResult.Restored -> LockUiState(
                    notice = restoredWhileLocked(result).takeIf { session.state.value is VaultState.Locked },
                )
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
        const val RESTORE_UNDONE =
            "Bóveda anterior recuperada. Está bloqueada: ábrela con su contraseña maestra. " +
                "La huella se ha desactivado; vuelve a activarla en Ajustes si quieres."

        /** Palabra que hay que teclear para restaurar sin la contraseña maestra actual (B-31). */
        const val FORCED_RESTORE_WORD = "RESTAURAR"

        /** Segundos de espera antes de poder confirmar esa restauración. */
        const val FORCED_RESTORE_DELAY_SECONDS = 5

        /** La confirmación fuerte está completa: la palabra exacta, sin más, y la espera cumplida. */
        fun forcedRestoreConfirmed(typed: String, secondsLeft: Int): Boolean =
            typed.trim() == FORCED_RESTORE_WORD && secondsLeft <= 0

        /** Qué contar cuando una restauración terminó con la bóveda bloqueada entretanto. */
        fun restoredWhileLocked(result: OperationResult.Restored): String =
            "Copia restaurada, pero la bóveda se bloqueó mientras tanto. Ábrela con la contraseña " +
                "maestra de la copia." +
                (if (result.hadUndo) " Puedes deshacer la restauración desde aquí o desde Ajustes." else "")

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
