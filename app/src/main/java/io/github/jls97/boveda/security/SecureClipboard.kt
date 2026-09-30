package io.github.jls97.boveda.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies secrets marked as sensitive (Android hides them from the clipboard preview and keyboards
 * don't keep them in their history) and clears the clipboard after a timeout or when locking.
 */
class SecureClipboard(context: Context, private val scope: CoroutineScope) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null

    fun copy(label: String, text: String, clearAfterSeconds: Int) {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        clipboard.setPrimaryClip(clip)
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(clearAfterSeconds * 1_000L)
            clipboard.clearPrimaryClip()
        }
    }

    /** Clears the clipboard now if something copied from the vault may still be on it. */
    fun clearIfPending() {
        if (clearJob?.isActive == true) {
            clearJob?.cancel()
            clipboard.clearPrimaryClip()
        }
    }
}
