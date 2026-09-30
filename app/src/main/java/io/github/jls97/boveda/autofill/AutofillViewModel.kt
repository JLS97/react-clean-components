package io.github.jls97.boveda.autofill

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jls97.boveda.core.autofill.CredentialMatcher
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.session.OperationResult
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import kotlinx.coroutines.launch
import java.util.UUID

internal class AutofillViewModel(private val session: VaultSession) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * Fills with [entry]. With [rememberChoice], links the app or site to it first, unless the
     * target can't be linked safely (see AutofillTarget.key).
     */
    fun pick(entry: VaultEntry, request: AutofillRequest.Fill, rememberChoice: Boolean, onReady: (VaultEntry) -> Unit) {
        if (busy) return
        if (!rememberChoice || request.target.key == null || CredentialMatcher.isExactMatch(entry, request.target)) {
            onReady(entry)
            return
        }
        busy = true
        viewModelScope.launch {
            val linked = CredentialMatcher.remember(entry, request.target)
            // If saving the link fails, still fill: the user asked for this entry.
            session.saveEntry(linked)
            busy = false
            onReady(linked)
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
                CredentialMatcher.remember(existing, pending.target)
                    .copy(username = username.trim(), password = pending.password, updatedAt = now)
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
                    autofillTargets = if (host == null) listOfNotNull(pending.target.key) else emptyList(),
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
