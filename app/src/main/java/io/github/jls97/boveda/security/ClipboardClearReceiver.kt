package io.github.jls97.boveda.security

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Fired by the alarm [SecureClipboard] arms on every copy. It runs even if Bóveda's process died
 * before the in-process timer (swiped from recents, killed by HyperOS), and only clears the
 * clipboard when the clip is still the one Bóveda copied. Not exported: only our own pending
 * intents reach it.
 */
class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SecureClipboard.ACTION_CLEAR) return
        val stamp = intent.getLongExtra(ClipboardClearPolicy.EXTRA_STAMP, -1L)
        val deadline = intent.getLongExtra(SecureClipboard.EXTRA_DEADLINE, -1L)
        if (stamp < 0L || deadline < 0L) return

        // Android ignores clipboard writes while the device is locked: try again a little later,
        // a bounded number of times (the system wipes the clipboard itself after about an hour).
        if (context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true) {
            val retries = intent.getIntExtra(SecureClipboard.EXTRA_RETRIES, 0)
            if (retries < MAX_RETRIES) {
                SecureClipboard.scheduleAlarm(
                    context, stamp, deadline,
                    triggerAtMs = SystemClock.elapsedRealtime() + RETRY_MS,
                    retries = retries + 1,
                )
            }
            return
        }
        SecureClipboard.clearIfOwn(context, stamp, deadline, force = false)
    }

    private companion object {
        const val RETRY_MS = 60_000L
        const val MAX_RETRIES = 60
    }
}
