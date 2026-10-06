package io.github.jls97.boveda.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies secrets marked as sensitive (Android hides them from the clipboard preview and keyboards
 * don't keep them in their history) under a neutral label, and clears the clipboard after a
 * timeout or when locking, only if the clip is still ours ([ClipboardClearPolicy]).
 *
 * The timeout runs twice: a coroutine in the process (precise) and an [AlarmManager] alarm to
 * [ClipboardClearReceiver] (survives the death of the process; inexact, so it may run a bit late).
 * Whichever runs first clears the clipboard and cancels the other.
 */
class SecureClipboard(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null

    /** Stamp and deadline (monotonic clock) of the clip Bóveda last copied; null when nothing is pending. */
    private var pending: PendingClip? = null

    private class PendingClip(val stamp: Long, val deadlineMs: Long)

    fun copy(text: String, clearAfterSeconds: Int) {
        val stamp = SystemClock.elapsedRealtime()
        val deadline = stamp + clearAfterSeconds * 1_000L
        val clip = ClipData.newPlainText(ClipboardClearPolicy.LABEL, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            putLong(ClipboardClearPolicy.EXTRA_STAMP, stamp)
        }
        clipboard.setPrimaryClip(clip)
        clearJob?.cancel()
        pending = PendingClip(stamp, deadline)
        scheduleAlarm(appContext, stamp, deadline)
        clearJob = scope.launch {
            delay(deadline - SystemClock.elapsedRealtime())
            finish(force = false)
        }
    }

    /** Clears the clipboard now if something copied from the vault may still be on it. */
    fun clearIfPending() {
        if (pending == null) return
        clearJob?.cancel()
        finish(force = true)
    }

    /** Ends the pending copy: clears the clipboard if the clip is still ours and drops the alarm. */
    private fun finish(force: Boolean) {
        val current = pending ?: return
        pending = null
        clearJob = null
        clearIfOwn(appContext, current.stamp, current.deadlineMs, force)
        cancelAlarm(appContext)
    }

    companion object {
        const val ACTION_CLEAR = "io.github.jls97.boveda.action.CLEAR_CLIPBOARD"
        const val EXTRA_DEADLINE = "io.github.jls97.boveda.CLIP_DEADLINE"
        const val EXTRA_RETRIES = "io.github.jls97.boveda.CLIP_RETRIES"

        /**
         * Applies [ClipboardClearPolicy] to the current clip and clears the clipboard if it says so.
         * Shared by the in-process paths and [ClipboardClearReceiver].
         */
        internal fun clearIfOwn(context: Context, stamp: Long, deadlineMs: Long, force: Boolean): ClipboardClearPolicy.Verdict {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
                ?: return ClipboardClearPolicy.Verdict.KEEP_FOREIGN
            val observed = clipboard.primaryClipDescription?.let { description ->
                val extras = description.extras
                ClipboardClearPolicy.ObservedClip(
                    label = description.label?.toString(),
                    stamp = extras?.takeIf { it.containsKey(ClipboardClearPolicy.EXTRA_STAMP) }
                        ?.getLong(ClipboardClearPolicy.EXTRA_STAMP),
                )
            }
            val verdict = ClipboardClearPolicy.decide(observed, stamp, SystemClock.elapsedRealtime(), deadlineMs, force)
            if (verdict == ClipboardClearPolicy.Verdict.CLEAR) clipboard.clearPrimaryClip()
            return verdict
        }

        /**
         * Arms the out-of-process clearing at [triggerAtMs] (monotonic clock). Inexact and allowed
         * while idle, so it needs no alarm permission; a new call replaces the previous alarm.
         */
        internal fun scheduleAlarm(
            context: Context,
            stamp: Long,
            deadlineMs: Long,
            triggerAtMs: Long = deadlineMs,
            retries: Int = 0,
        ) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = clearIntent(context)
                .putExtra(ClipboardClearPolicy.EXTRA_STAMP, stamp)
                .putExtra(EXTRA_DEADLINE, deadlineMs)
                .putExtra(EXTRA_RETRIES, retries)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, pendingIntent)
        }

        private fun cancelAlarm(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, clearIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            ) ?: return
            alarms.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        private fun clearIntent(context: Context): Intent =
            Intent(context, ClipboardClearReceiver::class.java).setAction(ACTION_CLEAR)

        private const val REQUEST_CODE = 0
    }
}
