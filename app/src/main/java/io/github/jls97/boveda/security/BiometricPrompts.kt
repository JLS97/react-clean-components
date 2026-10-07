package io.github.jls97.boveda.security

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import javax.crypto.Cipher

/** Fingerprint prompts tied to a Keystore cipher (the key only works after a real match). */
object BiometricPrompts {

    fun isStrongBiometricAvailable(context: Context): Boolean =
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system fingerprint dialog. [onResult] receives the authorized cipher, or null plus
     * an error message (null message when the user simply cancelled). The negative button
     * ([negativeLabel]) calls [onNegative] when given, so a screen can tell "Usar contraseña" from
     * a plain cancellation (back, touch outside); without it, it reports (null, null) like one.
     */
    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        cipher: Cipher,
        negativeLabel: String = "Usar contraseña",
        onNegative: (() -> Unit)? = null,
        onResult: (cipher: Cipher?, error: String?) -> Unit,
    ): CancellationSignal {
        val executor = activity.mainExecutor
        val cancellation = CancellationSignal()
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton(negativeLabel, executor) { _, _ -> if (onNegative != null) onNegative() else onResult(null, null) }
            .build()
        prompt.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            cancellation,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authorized = result.cryptoObject?.cipher
                    if (authorized != null) onResult(authorized, null) else onResult(null, "Autenticación incompleta")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // The negative button ("Usar contraseña", "Cancelar") goes to its own listener, not here.
                    val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    onResult(null, if (cancelled) null else errString.toString())
                }
            },
        )
        return cancellation
    }
}
