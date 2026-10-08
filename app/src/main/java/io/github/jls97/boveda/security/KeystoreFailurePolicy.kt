package io.github.jls97.boveda.security

import android.security.KeyStoreException
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException

/**
 * Pure decision of the Keystore managers: is a failure of the phone's Keystore permanent (the key
 * will never open its file again) or transient (the Keystore did not answer this time)? The
 * Android part, reading the error code out of the cause chain, is kept apart in
 * [keystoreErrorCode] so the decision itself can be tested on the JVM.
 *
 * It errs on the side of transient: a transient failure taken for a permanent one rotates the
 * layer key or offers the user to restore a backup, both of which destroy the vault on the phone
 * (M-07); a permanent failure taken for a transient one only asks the user to retry.
 * `UnrecoverableKeyException` in particular is not permanent by itself: `KeyStore.getKey` returns
 * null when the key does not exist and throws this exception for every other answer of the
 * keystore daemon, a restart or a busy StrongBox included, with the [KeyStoreException] that
 * says which as its cause.
 */
internal object KeystoreFailurePolicy {
    enum class Kind {
        /** The key is gone or can never open this file again. */
        PERMANENT,

        /** The Keystore did not answer; the key is intact and the same call may work later. */
        TRANSIENT,
    }

    /** Error codes of [KeyStoreException] after which the key is gone for good. */
    fun isPermanentErrorCode(numericErrorCode: Int): Boolean =
        numericErrorCode == KeyStoreException.ERROR_KEY_DOES_NOT_EXIST ||
            numericErrorCode == KeyStoreException.ERROR_KEY_CORRUPTED

    /**
     * Classifies an `UnrecoverableKeyException` by the error code of the [KeyStoreException] in
     * its cause chain ([keystoreErrorCode]), or `null` when there is none: permanent only when
     * the code says the key does not exist or is corrupted, transient otherwise.
     */
    fun classifyUnrecoverableKey(numericErrorCode: Int?): Kind =
        if (numericErrorCode != null && isPermanentErrorCode(numericErrorCode)) Kind.PERMANENT else Kind.TRANSIENT

    /**
     * Classifies any exception the Keystore paths may throw. [numericErrorCode] is the code read
     * from the cause chain of [e] with [keystoreErrorCode], `null` when there is none.
     */
    fun classify(e: Exception, numericErrorCode: Int?): Kind = when (e) {
        is KeyPermanentlyInvalidatedException, is AEADBadTagException -> Kind.PERMANENT
        is UnrecoverableKeyException -> classifyUnrecoverableKey(numericErrorCode)
        else -> Kind.TRANSIENT
    }

    /** Numeric error code of the first [KeyStoreException] in the cause chain of [e], or `null` if there is none. */
    fun keystoreErrorCode(e: Throwable): Int? =
        generateSequence(e) { it.cause?.takeIf { cause -> cause !== it } }
            .filterIsInstance<KeyStoreException>()
            .firstOrNull()
            ?.numericErrorCode
}
