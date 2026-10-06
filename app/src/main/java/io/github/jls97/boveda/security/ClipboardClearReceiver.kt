package io.github.jls97.boveda.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by the alarm [SecureClipboard] arms on every copy. It runs even if Bóveda's process died
 * before the in-process timer (swiped from recents, killed by HyperOS), and only clears the
 * clipboard when the clip is still the one Bóveda copied. Not exported: only our own pending
 * intents reach it.
 *
 * It clears whatever the keyguard state: Android only hides the clipboard from
 * *readers* without focus or while the device is locked; writes such as `clearPrimaryClip()` are
 * always accepted, and a hidden description is treated by [ClipboardClearPolicy] as still ours.
 */
class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SecureClipboard.ACTION_CLEAR) return
        val stamp = intent.getLongExtra(ClipboardClearPolicy.EXTRA_STAMP, -1L)
        val deadline = intent.getLongExtra(SecureClipboard.EXTRA_DEADLINE, -1L)
        if (stamp < 0L || deadline < 0L) return
        SecureClipboard.clearIfOwn(context, stamp, deadline, force = false)
    }
}
