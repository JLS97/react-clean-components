package io.github.jls97.boveda.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jls97.boveda.core.generator.PasswordStrength
import io.github.jls97.boveda.data.AntiPhishingPhrase
import io.github.jls97.boveda.data.antiPhishingPhraseProblem
import io.github.jls97.boveda.session.OperationResult
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import io.github.jls97.boveda.ui.theme.Personalidad
import io.github.jls97.boveda.ui.theme.elige
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
    /**
     * Contraseñas maestras incorrectas seguidas al desbloquear: cada una sacude el campo. Vuelve a 0
     * al desbloquear o restaurar; los fallos de otras operaciones no cuentan.
     */
    val wrongPasswords: Int = 0,
    /** El error es de la contraseña escrita en el campo de desbloqueo y se muestra bajo él. */
    val errorEnCampo: Boolean = false,
)

/** Drives the screens shown while the vault is closed: first-run setup and unlock. */
class LockViewModel(
    private val session: VaultSession,
    /** Registro de voz elegido en Ajustes; se lee en cada mensaje. */
    private val personalidad: () -> Personalidad = { Personalidad.Contrasenora },
) : ViewModel() {
    private val _ui = MutableStateFlow(LockUiState())
    val ui: StateFlow<LockUiState> = _ui.asStateFlow()

    /** Cambia cada vez que la pantalla vuelve al frente: sirve para releer el estado del teléfono. */
    val resumeTicks: StateFlow<Int> = session.resumeTicks

    /**
     * Crea la bóveda y, solo si sale bien, guarda [phrase] en [phrases] como frase antiphishing de la
     * pantalla de desbloqueo (M-04). La frase se comprueba antes de cifrar nada.
     */
    fun createVault(password: String, confirmation: String, phrase: String, phrases: AntiPhishingPhrase) {
        // Un segundo «Hecho» del teclado mientras la pantalla se va ya no puede rehacer la bóveda recién creada.
        if (_ui.value.busy || session.state.value !is VaultState.NoVault) return
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
        // Solo con la bóveda bloqueada: un Intro tardío en la pantalla que se va no vuelve a descifrar.
        if (_ui.value.busy || password.isEmpty() || session.state.value !is VaultState.Locked) return
        launchOperation(desbloqueo = true) { session.unlock(password.toCharArray()) }
    }

    fun isBiometricEnabled(): Boolean = session.isBiometricEnabled()

    fun biometricCipher(): Cipher? = session.biometricUnlockCipher()

    fun unlockWithBiometric(authorizedCipher: Cipher) {
        if (_ui.value.busy || session.state.value !is VaultState.Locked) return
        launchOperation { session.unlockWithBiometric(authorizedCipher) }
    }

    /** Restaura una copia cuando aún no hay bóveda en el teléfono (pantalla de creación). */
    fun restoreBackupFirstRun(backup: ByteArray, password: String) {
        if (_ui.value.busy || session.state.value !is VaultState.NoVault) return
        launchOperation { session.restoreBackupFirstRun(backup, password.toCharArray()) }
    }

    /**
     * Restaura una copia con la bóveda bloqueada. Hace falta [currentPassword], la contraseña
     * maestra de la bóveda que hay en el teléfono, o bien [forceWithoutCurrent] tras la
     * confirmación fuerte de la pantalla («no recuerdo la contraseña actual»): entonces no se
     * limpia el freno de intentos y la bóveda sustituida solo vuelve con «deshacer» (B-31).
     */
    fun restoreBackup(backup: ByteArray, password: String, currentPassword: String?, forceWithoutCurrent: Boolean) {
        if (_ui.value.busy || session.state.value !is VaultState.Locked) return
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
        _ui.value = _ui.value.copy(busy = false, error = message, errorEnCampo = false)
    }

    /** Un aviso (no un error) para la pantalla de bloqueo, p. ej. desde Ajustes al deshacer una restauración. */
    fun showNotice(message: String) {
        _ui.value = _ui.value.copy(busy = false, notice = message)
    }

    /**
     * Lanza [operation] y cuenta su resultado. [desbloqueo] es true solo para el desbloqueo con
     * contraseña: es el único fallo que se anuncia bajo el campo y sacude la pantalla. En las
     * restauraciones, una contraseña incorrecta es la de la copia y se dice así.
     */
    private fun launchOperation(
        successNotice: String? = null,
        desbloqueo: Boolean = false,
        operation: suspend () -> OperationResult,
    ) {
        val wrongPasswords = _ui.value.wrongPasswords
        // El bloqueo temporal sigue en pie mientras se prueba la huella o falla otra operación.
        val blockedUntil = _ui.value.blockedUntil
        _ui.value = LockUiState(busy = true, blockedUntil = blockedUntil, wrongPasswords = wrongPasswords)
        viewModelScope.launch {
            _ui.value = when (val result = operation()) {
                OperationResult.Success -> LockUiState(notice = successNotice)
                // La pantalla desaparece al desbloquearse; el aviso solo importa si la bóveda se
                // bloqueó mientras se escribía la copia y sigue aquí (R01-5).
                is OperationResult.Restored -> LockUiState(
                    notice = restoredWhileLocked(result).takeIf { session.state.value is VaultState.Locked },
                )
                OperationResult.WrongPassword -> if (desbloqueo) {
                    LockUiState(
                        error = personalidad().elige(
                            "Esa no es. Revisa mayúsculas y vuelve a intentarlo.",
                            "Contraseña incorrecta. Revisa mayúsculas y vuelve a intentarlo.",
                        ),
                        wrongPasswords = wrongPasswords + 1,
                        errorEnCampo = true,
                    )
                } else {
                    LockUiState(
                        error = "La contraseña de la copia no es correcta.",
                        blockedUntil = blockedUntil,
                        wrongPasswords = wrongPasswords,
                    )
                }
                OperationResult.WrongCurrentPassword -> LockUiState(
                    error = "La contraseña maestra actual no es correcta.",
                    blockedUntil = blockedUntil,
                    wrongPasswords = wrongPasswords,
                )
                is OperationResult.Throttled -> LockUiState(
                    // La lápida ya dice cuánto falta; fuera del desbloqueo hay que decir además qué no se ha hecho.
                    error = if (desbloqueo) {
                        "Demasiados intentos fallidos."
                    } else {
                        "La copia no se ha restaurado: espera a que termine el bloqueo por intentos fallidos."
                    },
                    blockedUntil = result.untilMillis,
                    wrongPasswords = wrongPasswords,
                    errorEnCampo = desbloqueo,
                )
                is OperationResult.Failure -> LockUiState(
                    error = result.message,
                    blockedUntil = blockedUntil,
                    wrongPasswords = wrongPasswords,
                )
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
