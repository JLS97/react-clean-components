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
     * an error message (null message when the user simply cancelled).
     */
    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        cipher: Cipher,
        onResult: (cipher: Cipher?, error: String?) -> Unit,
    ): CancellationSignal {
        val executor = activity.mainExecutor
        val cancellation = CancellationSignal()
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton("Usar contraseña", executor) { _, _ -> onResult(null, null) }
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
                    val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON
                    onResult(null, if (cancelled) null else errString.toString())
                }
            },
        )
        return cancellation
    }
}
