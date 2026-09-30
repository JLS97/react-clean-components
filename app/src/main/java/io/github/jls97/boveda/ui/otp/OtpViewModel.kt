package io.github.jls97.boveda.ui.otp

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jls97.boveda.core.crypto.wipe
import io.github.jls97.boveda.core.otp.OtpInput
import io.github.jls97.boveda.core.otp.OtpInputError
import io.github.jls97.boveda.core.otp.OtpInputResult
import io.github.jls97.boveda.core.otp.OtpParams
import io.github.jls97.boveda.core.otp.OtpSecret
import io.github.jls97.boveda.core.otp.RecoveryCode
import io.github.jls97.boveda.core.vault.VaultSettings
import io.github.jls97.boveda.session.OperationResult
import io.github.jls97.boveda.session.OtpAccess
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.crypto.Cipher

enum class RecoveryCodePurpose { SETUP, REPLACE }

/** A code shown after a fingerprint. Its secret is wiped when it is hidden. */
class RevealedOtp(val entryId: String, val secret: OtpSecret, val revealedAt: Long) {
    val expired: Boolean get() = SystemClock.elapsedRealtime() - revealedAt >= REVEAL_MILLIS

    companion object {
        /** A revealed code stays on screen (changing every period) for one minute at most. */
        const val REVEAL_MILLIS = 60_000L
    }
}

/**
 * 2FA screens: the code being added, the recovery code being shown, and the code revealed with a
 * fingerprint. Secrets only stay here while they are on screen, and everything is wiped when the
 * vault locks.
 */
class OtpViewModel(private val session: VaultSession) : ViewModel() {
    var busy by mutableStateOf(false)
        private set

    var revealed by mutableStateOf<RevealedOtp?>(null)
        private set

    // The "add a code" form.
    var addEntryId by mutableStateOf<String?>(null)
        private set
    var input by mutableStateOf("")
        private set
    var manualParams by mutableStateOf(OtpParams.DEFAULT)
        private set
    var inputError by mutableStateOf<OtpInputError?>(null)
        private set
    var pending by mutableStateOf<OtpSecret?>(null)
        private set

    // The recovery code of a setup or a replacement, until it is confirmed.
    private var recoveryCode: CharArray? = null
    var recoveryCodeText by mutableStateOf("")
        private set

    // A recovery code already checked, waiting for the fingerprint.
    private var checkedRecoveryCode: CharArray? = null

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            session.state.collect { state -> if (state !is VaultState.Unlocked) forgetEverything() }
        }
    }

    override fun onCleared() {
        forgetEverything()
    }

    private fun forgetEverything() {
        hide()
        clearDraft()
        clearRecoveryCode()
        clearCheckedRecoveryCode()
    }

    private val otpAccess: OtpAccess
        get() = (session.state.value as? VaultState.Unlocked)?.otpAccess ?: OtpAccess.NONE

    private val clipboardSeconds: Int
        get() = (session.state.value as? VaultState.Unlocked)?.data?.settings?.clipboardClearSeconds
            ?: VaultSettings.DEFAULT_CLIPBOARD_CLEAR_SECONDS

    // region Adding a code

    fun startAdd(entryId: String) {
        clearDraft()
        addEntryId = entryId
    }

    fun updateInput(text: String) {
        input = text
        reparse()
    }

    fun updateParams(params: OtpParams) {
        manualParams = params
        reparse()
    }

    /** True when the input is an otpauth link, which carries its own parameters. */
    val inputIsLink: Boolean get() = input.trim().startsWith("otpauth", ignoreCase = true)

    private fun reparse() {
        pending?.wipe()
        pending = null
        inputError = null
        if (input.isBlank()) return
        when (val result = OtpInput.parse(input, manualParams)) {
            is OtpInputResult.Valid -> pending = result.secret
            is OtpInputResult.Invalid -> inputError = result.error
        }
    }

    fun clearDraft() {
        pending?.wipe()
        pending = null
        input = ""
        manualParams = OtpParams.DEFAULT
        inputError = null
        addEntryId = null
    }

    /** Saves the pending code once 2FA is set up (fingerprint of the 2FA key in [authorized]). */
    fun add(authorized: Cipher, onDone: () -> Unit) {
        val entryId = addEntryId ?: return
        val secret = pending ?: return
        launchBusy {
            when (val result = session.addOtp(authorized, entryId, secret)) {
                OperationResult.Success -> {
                    clearDraft()
                    message("Código 2FA guardado. Solo se abre con tu huella.")
                    onDone()
                }
                is OperationResult.Failure -> message(result.message)
                else -> message("No se pudo guardar el código 2FA.")
            }
        }
    }

    // endregion

    // region Recovery code (first code and replacement)

    fun beginRecoveryCode() {
        clearRecoveryCode()
        val code = RecoveryCode.generate()
        recoveryCode = code
        recoveryCodeText = RecoveryCode.format(code)
    }

    fun recoveryCodeMatches(typed: String): Boolean {
        val expected = recoveryCode ?: return false
        val normalized = RecoveryCode.normalize(typed) ?: return false
        return normalized.contentEquals(expected).also { normalized.wipe() }
    }

    fun clearRecoveryCode() {
        recoveryCode?.wipe()
        recoveryCode = null
        recoveryCodeText = ""
    }

    /** First code: creates the 2FA key with the fingerprint in [authorized] and the recovery code. */
    fun setUp(authorized: Cipher, onDone: () -> Unit) {
        val entryId = addEntryId ?: return
        val secret = pending ?: return
        val code = recoveryCode ?: return
        launchBusy {
            when (val result = session.setUpOtp(authorized, code, entryId, secret)) {
                OperationResult.Success -> {
                    clearRecoveryCode()
                    clearDraft()
                    message("Código 2FA guardado. Cada código se abre solo con tu huella.")
                    onDone()
                }
                is OperationResult.Failure -> message(result.message)
                else -> message("No se pudieron activar los códigos 2FA.")
            }
        }
    }

    fun replaceRecoveryCode(authorized: Cipher, onDone: () -> Unit) {
        val code = recoveryCode ?: return
        launchBusy {
            when (val result = session.replaceOtpRecoveryCode(authorized, code)) {
                OperationResult.Success -> {
                    clearRecoveryCode()
                    message("Código de recuperación cambiado. Las copias anteriores siguen necesitando el antiguo.")
                    onDone()
                }
                is OperationResult.Failure -> message(result.message)
                else -> message("No se pudo cambiar el código de recuperación.")
            }
        }
    }

    // endregion

    // region Recovery on this phone

    /** Checks [typed] against the vault (slow), then calls [onValid] to ask for the fingerprint. */
    fun checkRecoveryCode(typed: String, onValid: () -> Unit) {
        val code = RecoveryCode.normalize(typed)
        if (code == null) {
            message("El código de recuperación tiene 20 caracteres, en 4 grupos de 5.")
            return
        }
        launchBusy {
            if (session.checkOtpRecoveryCode(code)) {
                clearCheckedRecoveryCode()
                checkedRecoveryCode = code
                onValid()
            } else {
                code.wipe()
                message("Ese no es el código de recuperación de esta bóveda.")
            }
        }
    }

    fun clearCheckedRecoveryCode() {
        checkedRecoveryCode?.wipe()
        checkedRecoveryCode = null
    }

    fun recover(authorized: Cipher, onDone: () -> Unit) {
        val code = checkedRecoveryCode ?: return
        launchBusy {
            when (val result = session.recoverOtp(authorized, code)) {
                OperationResult.Success -> {
                    clearCheckedRecoveryCode()
                    message("Códigos 2FA recuperados. Desde ahora se abren con tu huella.")
                    onDone()
                }
                OperationResult.WrongPassword -> message("Ese no es el código de recuperación de esta bóveda.")
                is OperationResult.Failure -> message(result.message)
                else -> message("No se pudieron recuperar los códigos 2FA.")
            }
        }
    }

    // endregion

    // region Showing, copying and removing

    /** Cipher of the 2FA key for a fingerprint prompt, or null after explaining why not. */
    fun unlockCipher(): Cipher? {
        val cipher = session.otpUnlockCipher()
        if (cipher == null) {
            message(
                if (otpAccess == OtpAccess.LOCKED) {
                    "Tus huellas han cambiado, así que los códigos 2FA están bloqueados en este móvil. " +
                        "Recupéralos con tu código de recuperación."
                } else {
                    "No se pudo preparar la huella."
                },
            )
        }
        return cipher
    }

    /** Cipher of a new fingerprint key for a prompt, or null after explaining why not. */
    fun enrollmentCipher(): Cipher? =
        session.otpEnrollmentCipher().also { if (it == null) message("No se pudo preparar la huella. Comprueba que tienes una registrada.") }

    fun reveal(authorized: Cipher, entryId: String, copy: Boolean) {
        launchBusy {
            val secret = session.revealOtp(authorized, entryId)
            if (secret == null) {
                message("No se pudo abrir el código 2FA.")
                return@launchBusy
            }
            hide()
            revealed = RevealedOtp(entryId, secret, SystemClock.elapsedRealtime())
            if (copy) copyCode()
        }
    }

    fun copyCode() {
        val current = revealed ?: return
        val now = System.currentTimeMillis()
        val seconds = clipboardSeconds
        session.clipboard.copy("Código 2FA", current.secret.code(now), seconds)
        message("Código copiado: cambia en ${current.secret.secondsLeft(now)} s y se borrará del portapapeles en $seconds s.")
    }

    /** Hides the revealed code (only if it belongs to [entryId], when given) and wipes its secret. */
    fun hide(entryId: String? = null) {
        val current = revealed ?: return
        if (entryId != null && current.entryId != entryId) return
        revealed = null
        current.secret.wipe()
    }

    fun remove(entryId: String) {
        hide(entryId)
        launchBusy {
            when (val result = session.removeOtp(entryId)) {
                OperationResult.Success -> message("Código 2FA quitado de la entrada.")
                is OperationResult.Failure -> message(result.message)
                else -> message("No se pudo quitar el código 2FA.")
            }
        }
    }

    // endregion

    fun message(text: String) {
        _messages.trySend(text)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message("Error inesperado.")
            } finally {
                busy = false
            }
        }
    }
}

/** What went wrong with a typed or scanned 2FA key, for the person reading it. */
fun otpInputErrorText(error: OtpInputError): String = when (error) {
    OtpInputError.EMPTY -> "Escribe o escanea la clave."
    OtpInputError.NOT_TIME_BASED -> "Es un código por contador (HOTP). Bóveda solo guarda códigos por tiempo (TOTP), los habituales."
    OtpInputError.MIGRATION_EXPORT ->
        "Es una exportación de Google Authenticator. Escanea en su lugar el QR que da cada web al activar la verificación."
    OtpInputError.NOT_OTPAUTH -> "Eso es un enlace, no una clave 2FA."
    OtpInputError.MISSING_SECRET -> "El enlace no incluye la clave secreta."
    OtpInputError.INVALID_SECRET -> "La clave solo puede tener letras de la A a la Z y números del 2 al 7."
    OtpInputError.SECRET_TOO_SHORT -> "La clave es demasiado corta. Comprueba que la has copiado entera."
    OtpInputError.UNSUPPORTED_ALGORITHM -> "Usa un algoritmo que Bóveda no admite."
    OtpInputError.INVALID_DIGITS -> "Pide un número de cifras que Bóveda no admite (de 6 a 8)."
    OtpInputError.INVALID_PERIOD -> "Pide un periodo que Bóveda no admite."
}
