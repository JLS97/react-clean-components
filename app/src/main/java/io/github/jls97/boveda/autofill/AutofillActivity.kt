package io.github.jls97.boveda.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.View
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.jls97.boveda.BovedaApplication
import io.github.jls97.boveda.core.autofill.AutofillTarget
import io.github.jls97.boveda.core.autofill.TargetResolver
import io.github.jls97.boveda.core.crypto.secureRandom
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import io.github.jls97.boveda.ui.theme.BovedaTheme
import kotlinx.coroutines.launch

/** What the system asked for when it opened [AutofillActivity]. */
internal sealed interface AutofillRequest {
    data class Fill(val target: AutofillTarget, val usernameId: AutofillId?, val passwordId: AutofillId?) : AutofillRequest

    data class FillOtp(val target: AutofillTarget, val otpId: AutofillId) : AutofillRequest

    /** [pending] is null when the credentials expired or were already consumed. */
    data class Save(val token: String, val pending: PendingSave?) : AutofillRequest
}

/**
 * Opened by the system from the "Bóveda" suggestion (to fill) or from "Save to Bóveda". It shows
 * the unlock screen if needed and then lets the user pick or save an entry. If the vault was
 * locked at any moment while this screen existed (when it opened, or meanwhile because of the
 * screen going off or the timer), it is locked again as soon as this screen closes: an unlock
 * made here only served to fill.
 */
class AutofillActivity : ComponentActivity() {
    private val session: VaultSession get() = (application as BovedaApplication).session

    /** True once the vault has been seen locked while this activity (or its previous instance) existed. */
    private var lockedWhileOpen = true
    private var saveToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        setRecentsScreenshotEnabled(false)
        window.setHideOverlayWindows(true)
        enableEdgeToEdge()
        with(window.decorView) {
            filterTouchesWhenObscured = true
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        lockedWhileOpen = savedInstanceState?.getBoolean(STATE_LOCKED_WHILE_OPEN)
            ?: (session.state.value !is VaultState.Unlocked)
        // Any lock while this screen lives (screen off, timer, background) means the unlock that
        // follows happened here, only to fill: the vault must not stay open afterwards.
        lifecycleScope.launch {
            session.state.collect { if (it !is VaultState.Unlocked) lockedWhileOpen = true }
        }
        val request = readRequest(this, intent)
        if (request == null) {
            // Missing or malformed extras: nothing to show, and nothing is told to the caller.
            finish()
            return
        }
        saveToken = (request as? AutofillRequest.Save)?.token
        setContent {
            BovedaTheme {
                AutofillApp(
                    session = session,
                    request = request,
                    onFilled = { dataset -> finishWith(dataset) },
                    onClose = { finishWith(null) },
                )
            }
        }
    }

    private fun finishWith(dataset: Dataset?) {
        // Closing or cancelling the save screen is the end of those credentials, whatever follows.
        saveToken?.let { PendingSaves.remove(it) }
        if (dataset != null) {
            setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCKED_WHILE_OPEN, lockedWhileOpen)
    }

    override fun onStart() {
        super.onStart()
        session.onAppForeground()
    }

    override fun onResume() {
        super.onResume()
        session.onAppResumed()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) session.onAppBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            saveToken?.let { PendingSaves.remove(it) }
            // An unlock that only happened to fill a form doesn't leave the vault open.
            if (lockedWhileOpen) session.lock()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        session.touch()
    }

    companion object {
        private const val EXTRA_MODE = "io.github.jls97.boveda.autofill.MODE"
        private const val EXTRA_PACKAGE = "io.github.jls97.boveda.autofill.PACKAGE"
        private const val EXTRA_REPORTED_WEB_DOMAIN = "io.github.jls97.boveda.autofill.REPORTED_WEB_DOMAIN"
        private const val EXTRA_WEB_SCHEME = "io.github.jls97.boveda.autofill.WEB_SCHEME"
        private const val EXTRA_USERNAME_ID = "io.github.jls97.boveda.autofill.USERNAME_ID"
        private const val EXTRA_PASSWORD_ID = "io.github.jls97.boveda.autofill.PASSWORD_ID"
        private const val EXTRA_OTP_ID = "io.github.jls97.boveda.autofill.OTP_ID"
        private const val EXTRA_SAVE_TOKEN = "io.github.jls97.boveda.autofill.SAVE_TOKEN"
        private const val MODE_FILL = "fill"
        private const val MODE_SAVE = "save"
        private const val MODE_OTP = "otp"
        private const val STATE_LOCKED_WHILE_OPEN = "locked_while_open"

        /**
         * Carries only field ids and who is asking; never a secret. Trust in the web domain is
         * decided when the activity opens, from the app's verified signature. [reportedWebDomain]
         * and [webScheme] are those of the fields to fill, as reported by the app.
         *
         * The PendingIntent is mutable because the framework fills its authentication extras
         * (assist structure, client state) into the intent before launching it, and it is the app
         * being filled, not the system, that sends it (AutofillManager.authenticate). That app can
         * send it again with a fill-in intent of its own, so nothing here may depend on the extras
         * it could add: `Intent.fillIn` never replaces an extra already present, every key read by
         * [readRequest] is put here (with null when there is no value) so the base intent always
         * wins, and the identity of the caller is recomputed from PackageManager (its verified
         * signing certificate) rather than trusted from the extra. The component is explicit, so
         * only this activity opens.
         */
        internal fun fillIntentSender(
            context: Context,
            packageName: String,
            reportedWebDomain: String?,
            webScheme: String?,
            usernameId: AutofillId?,
            passwordId: AutofillId?,
        ): IntentSender {
            val intent = Intent(context, AutofillActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_FILL)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_REPORTED_WEB_DOMAIN, reportedWebDomain)
                .putExtra(EXTRA_WEB_SCHEME, webScheme)
                .putExtra(EXTRA_USERNAME_ID, usernameId)
                .putExtra(EXTRA_PASSWORD_ID, passwordId)
                .putExtra(EXTRA_SAVE_TOKEN, null as String?)
            return PendingIntent.getActivity(
                context,
                secureRandom.nextInt(),
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ).intentSender
        }

        /** Like [fillIntentSender], for the field of a 2FA code. */
        internal fun otpIntentSender(
            context: Context,
            packageName: String,
            reportedWebDomain: String?,
            webScheme: String?,
            otpId: AutofillId,
        ): IntentSender {
            val intent = Intent(context, AutofillActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_OTP)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_REPORTED_WEB_DOMAIN, reportedWebDomain)
                .putExtra(EXTRA_WEB_SCHEME, webScheme)
                .putExtra(EXTRA_OTP_ID, otpId)
                .putExtra(EXTRA_SAVE_TOKEN, null as String?)
            // Mutable for the same reason as the fill intent.
            return PendingIntent.getActivity(
                context,
                secureRandom.nextInt(),
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ).intentSender
        }

        internal fun saveIntentSender(context: Context, token: String): IntentSender {
            val intent = Intent(context, AutofillActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_SAVE)
                .putExtra(EXTRA_SAVE_TOKEN, token)
            return PendingIntent.getActivity(
                context,
                secureRandom.nextInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ).intentSender
        }

        /**
         * What was asked, or null when the extras are missing or malformed (a package name that
         * isn't one, a mode without its field ids): the activity then closes without showing
         * anything. The package name is only a lookup key; who is asking is decided by
         * [AppSigners.resolveTarget] from the signing certificate the package manager verified.
         */
        private fun readRequest(context: Context, intent: Intent): AutofillRequest? = when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_FILL -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE)
                val usernameId = intent.getParcelableExtra(EXTRA_USERNAME_ID, AutofillId::class.java)
                val passwordId = intent.getParcelableExtra(EXTRA_PASSWORD_ID, AutofillId::class.java)
                if (packageName == null || !TargetResolver.isPackageName(packageName) || (usernameId == null && passwordId == null)) {
                    null
                } else {
                    AutofillRequest.Fill(
                        target = AppSigners.resolveTarget(
                            context,
                            packageName,
                            intent.getStringExtra(EXTRA_REPORTED_WEB_DOMAIN),
                            intent.getStringExtra(EXTRA_WEB_SCHEME),
                        ),
                        usernameId = usernameId,
                        passwordId = passwordId,
                    )
                }
            }
            MODE_OTP -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE)
                val otpId = intent.getParcelableExtra(EXTRA_OTP_ID, AutofillId::class.java)
                if (packageName == null || !TargetResolver.isPackageName(packageName) || otpId == null) {
                    null
                } else {
                    AutofillRequest.FillOtp(
                        target = AppSigners.resolveTarget(
                            context,
                            packageName,
                            intent.getStringExtra(EXTRA_REPORTED_WEB_DOMAIN),
                            intent.getStringExtra(EXTRA_WEB_SCHEME),
                        ),
                        otpId = otpId,
                    )
                }
            }
            MODE_SAVE -> intent.getStringExtra(EXTRA_SAVE_TOKEN)?.takeIf { it.isNotEmpty() }?.let { token ->
                AutofillRequest.Save(token, PendingSaves.get(token))
            }
            else -> null
        }
    }
}
