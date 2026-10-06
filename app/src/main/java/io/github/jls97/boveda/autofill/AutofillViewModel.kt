package io.github.jls97.boveda.autofill

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jls97.boveda.core.autofill.AutofillTarget
import io.github.jls97.boveda.core.autofill.CredentialMatcher
import io.github.jls97.boveda.core.autofill.SaveCapture
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.session.OperationResult
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import kotlinx.coroutines.launch
import java.util.UUID
import javax.crypto.Cipher

internal class AutofillViewModel(private val session: VaultSession) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun showError(message: String) {
        error = message
    }

    /**
     * Fills with [entry]. With [rememberChoice], links the app or site to it first, unless the
     * target can't be linked safely (see AutofillTarget.key).
     */
    fun pick(
        entry: VaultEntry,
        target: AutofillTarget,
        rememberChoice: Boolean,
        onLocked: () -> Unit,
        onReady: (VaultEntry) -> Unit,
    ) {
        if (busy) return
        if (!rememberChoice || target.key == null || CredentialMatcher.isExactMatch(entry, target) || impersonates(target)) {
            onReady(entry)
            return
        }
        busy = true
        viewModelScope.launch {
            val linked = CredentialMatcher.remember(entry, target)
            // If saving the link fails, still fill: the user asked for this entry.
            session.saveEntry(linked)
            busy = false
            // Unless the vault locked while waiting for the write (screen off, another screen
            // holding it): after a lock nothing decrypted leaves, not even an entry just chosen.
            if (session.state.value !is VaultState.Unlocked) {
                onLocked()
                return@launch
            }
            onReady(linked)
        }
    }

    /** True when [target] shares its package name with a linked app but not its signature: never linked. */
    private fun impersonates(target: AutofillTarget): Boolean {
        val entries = (session.state.value as? VaultState.Unlocked)?.data?.entries.orEmpty()
        return CredentialMatcher.impersonationWarnings(entries, target).isNotEmpty()
    }

    /** Cipher of the 2FA key for the fingerprint prompt, or null (with an error shown) if it can't open. */
    fun otpCipher(): Cipher? = session.otpUnlockCipher().also {
        if (it == null) error = "No se pudo preparar la huella. Abre Bóveda para revisar tus códigos 2FA."
    }

    /** Opens the 2FA secret of [entry] with the fingerprint and hands over only its current code. */
    fun fillCode(authorized: Cipher, entry: VaultEntry, onCode: (String) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val secret = session.revealOtp(authorized, entry.id)
            busy = false
            if (secret == null) {
                error = "No se pudo abrir el código 2FA."
                return@launch
            }
            val code = try {
                secret.code(System.currentTimeMillis())
            } finally {
                secret.wipe()
            }
            onCode(code)
        }
    }

    /** Saves the typed credentials as a new entry, or into [replaceId] if given. */
    fun save(pending: PendingSave, title: String, username: String, replaceId: String?, onDone: () -> Unit) {
        if (busy) return
        if (title.isBlank()) {
            error = "Ponle un nombre a la entrada."
            return
        }
        busy = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entries = (session.state.value as? VaultState.Unlocked)?.data?.entries.orEmpty()
            val existing = replaceId?.let { id -> entries.find { it.id == id } }
            val entry = if (existing != null) {
                // A form without a user field (a password change) must not empty the stored one.
                CredentialMatcher.remember(existing, pending.target)
                    .copy(username = SaveCapture.mergedUsername(existing, username), password = pending.password, updatedAt = now)
            } else {
                val host = pending.target.host
                VaultEntry(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    username = username.trim(),
                    password = pending.password,
                    url = host.orEmpty(),
                    createdAt = now,
                    updatedAt = now,
                    // Web sites match through the url; apps through a link, if they can be linked.
                    autofillTargets = if (host == null && !impersonates(pending.target)) listOfNotNull(pending.target.key) else emptyList(),
                )
            }
            val result = session.saveEntry(entry)
            busy = false
            if (result is OperationResult.Success) {
                onDone()
            } else {
                error = (result as? OperationResult.Failure)?.message ?: "No se pudo guardar."
            }
        }
    }
}
