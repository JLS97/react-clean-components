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
import io.github.jls97.boveda.BovedaApplication
import io.github.jls97.boveda.core.autofill.AutofillTarget
import io.github.jls97.boveda.core.crypto.secureRandom
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import io.github.jls97.boveda.ui.theme.BovedaTheme

/** What the system asked for when it opened [AutofillActivity]. */
internal sealed interface AutofillRequest {
    data class Fill(val target: AutofillTarget, val usernameId: AutofillId?, val passwordId: AutofillId?) : AutofillRequest

    data class FillOtp(val target: AutofillTarget, val otpId: AutofillId) : AutofillRequest

    data class Save(val pending: PendingSave?) : AutofillRequest
}

/**
 * Opened by the system from the "Bóveda" suggestion (to fill) or from "Save to Bóveda". It shows
 * the unlock screen if needed and then lets the user pick or save an entry. If the vault was
 * locked when it opened, it is locked again as soon as this screen closes.
 */
class AutofillActivity : ComponentActivity() {
    private val session: VaultSession get() = (application as BovedaApplication).session
    private var wasLockedAtStart = true
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

        wasLockedAtStart = savedInstanceState?.getBoolean(STATE_WAS_LOCKED)
            ?: (session.state.value !is VaultState.Unlocked)
        saveToken = intent.getStringExtra(EXTRA_SAVE_TOKEN)
        val request = readRequest(this, intent)
        if (request == null) {
            finish()
            return
        }
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
        if (dataset != null) {
            setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_WAS_LOCKED, wasLockedAtStart)
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
            if (wasLockedAtStart) session.lock()
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
        private const val EXTRA_USERNAME_ID = "io.github.jls97.boveda.autofill.USERNAME_ID"
        private const val EXTRA_PASSWORD_ID = "io.github.jls97.boveda.autofill.PASSWORD_ID"
        private const val EXTRA_OTP_ID = "io.github.jls97.boveda.autofill.OTP_ID"
        private const val EXTRA_SAVE_TOKEN = "io.github.jls97.boveda.autofill.SAVE_TOKEN"
        private const val MODE_FILL = "fill"
        private const val MODE_SAVE = "save"
        private const val MODE_OTP = "otp"
        private const val STATE_WAS_LOCKED = "was_locked"

        /**
         * Carries only field ids and who is asking; never a secret. Trust in the web domain is
         * decided when the activity opens, from the app's verified signature.
         */
        internal fun fillIntentSender(
            context: Context,
            packageName: String,
            reportedWebDomain: String?,
            usernameId: AutofillId?,
            passwordId: AutofillId?,
        ): IntentSender {
            val intent = Intent(context, AutofillActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_FILL)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_REPORTED_WEB_DOMAIN, reportedWebDomain)
                .putExtra(EXTRA_USERNAME_ID, usernameId)
                .putExtra(EXTRA_PASSWORD_ID, passwordId)
            // Mutable because the platform adds its authentication extras to this intent
            // (see Dataset.Builder.setAuthentication). It is explicit, so only this activity opens.
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
            otpId: AutofillId,
        ): IntentSender {
            val intent = Intent(context, AutofillActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_OTP)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_REPORTED_WEB_DOMAIN, reportedWebDomain)
                .putExtra(EXTRA_OTP_ID, otpId)
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

        private fun readRequest(context: Context, intent: Intent): AutofillRequest? = when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_FILL -> intent.getStringExtra(EXTRA_PACKAGE)?.let { packageName ->
                AutofillRequest.Fill(
                    target = AppSigners.resolveTarget(context, packageName, intent.getStringExtra(EXTRA_REPORTED_WEB_DOMAIN)),
                    usernameId = intent.getParcelableExtra(EXTRA_USERNAME_ID, AutofillId::class.java),
                    passwordId = intent.getParcelableExtra(EXTRA_PASSWORD_ID, AutofillId::class.java),
                )
            }
            MODE_OTP -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE)
                val otpId = intent.getParcelableExtra(EXTRA_OTP_ID, AutofillId::class.java)
                if (packageName == null || otpId == null) {
                    null
                } else {
                    AutofillRequest.FillOtp(
                        target = AppSigners.resolveTarget(context, packageName, intent.getStringExtra(EXTRA_REPORTED_WEB_DOMAIN)),
                        otpId = otpId,
                    )
                }
            }
            MODE_SAVE -> AutofillRequest.Save(intent.getStringExtra(EXTRA_SAVE_TOKEN)?.let { PendingSaves.get(it) })
            else -> null
        }
    }
}
