package io.github.jls97.boveda.security

import android.security.KeyStoreException
import io.github.jls97.boveda.security.KeystoreFailurePolicy.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException

/**
 * When a Keystore failure counts as permanent (rotate the layer key, offer to restore) and when
 * as transient (retry, never touch the key): M-07 and R03-2.
 */
class KeystoreFailurePolicyTest {

    @Test
    fun onlyKeyGoneOrCorruptedCodesArePermanent() {
        assertTrue(KeystoreFailurePolicy.isPermanentErrorCode(KeyStoreException.ERROR_KEY_DOES_NOT_EXIST))
        assertTrue(KeystoreFailurePolicy.isPermanentErrorCode(KeyStoreException.ERROR_KEY_CORRUPTED))
        for (code in listOf(
            KeyStoreException.ERROR_INTERNAL_SYSTEM_ERROR,
            KeyStoreException.ERROR_KEYSTORE_FAILURE,
            KeyStoreException.ERROR_KEYMINT_FAILURE,
            KeyStoreException.ERROR_KEYSTORE_UNINITIALIZED,
            KeyStoreException.ERROR_USER_AUTHENTICATION_REQUIRED,
            KeyStoreException.ERROR_KEY_NOT_TEMPORALLY_VALID,
            KeyStoreException.ERROR_PERMISSION_DENIED,
            KeyStoreException.ERROR_OTHER,
        )) {
            assertFalse("código $code", KeystoreFailurePolicy.isPermanentErrorCode(code))
        }
    }

    @Test
    fun unrecoverableKeyIsTransientUnlessTheKeystoreSaysTheKeyIsGone() {
        // "Failed to obtain information about key" with SYSTEM_ERROR: the daemon is restarting.
        assertEquals(Kind.TRANSIENT, KeystoreFailurePolicy.classifyUnrecoverableKey(KeyStoreException.ERROR_INTERNAL_SYSTEM_ERROR))
        // No KeyStoreException in the chain: nothing says the key is gone, so never rotate.
        assertEquals(Kind.TRANSIENT, KeystoreFailurePolicy.classifyUnrecoverableKey(null))
        assertEquals(Kind.PERMANENT, KeystoreFailurePolicy.classifyUnrecoverableKey(KeyStoreException.ERROR_KEY_DOES_NOT_EXIST))
        assertEquals(Kind.PERMANENT, KeystoreFailurePolicy.classifyUnrecoverableKey(KeyStoreException.ERROR_KEY_CORRUPTED))
    }

    @Test
    fun classifyFollowsTheExceptionType() {
        val unrecoverable = UnrecoverableKeyException("Failed to obtain information about key")
        // What DeviceKeyManager.loadOrCreate sees on a transient keystore failure: not a
        // DeviceBindingException, so no new key is created over the one the vault is sealed with.
        assertEquals(Kind.TRANSIENT, KeystoreFailurePolicy.classify(unrecoverable, KeyStoreException.ERROR_INTERNAL_SYSTEM_ERROR))
        assertEquals(Kind.TRANSIENT, KeystoreFailurePolicy.classify(unrecoverable, null))
        assertEquals(Kind.PERMANENT, KeystoreFailurePolicy.classify(unrecoverable, KeyStoreException.ERROR_KEY_DOES_NOT_EXIST))
        // A wrapped key the Keystore key no longer opens is gone for good, whatever the code says.
        assertEquals(Kind.PERMANENT, KeystoreFailurePolicy.classify(AEADBadTagException(), null))
        assertEquals(Kind.PERMANENT, KeystoreFailurePolicy.classify(AEADBadTagException(), KeyStoreException.ERROR_INTERNAL_SYSTEM_ERROR))
        // The Keystore refusing to work right now.
        assertEquals(Kind.TRANSIENT, KeystoreFailurePolicy.classify(ProviderException("Keystore operation failed"), null))
        assertEquals(Kind.TRANSIENT, KeystoreFailurePolicy.classify(IllegalBlockSizeException(), null))
        assertEquals(Kind.TRANSIENT, KeystoreFailurePolicy.classify(java.security.KeyStoreException("busy"), null))
    }

    @Test
    fun errorCodeIsNullWithoutAKeystoreExceptionInTheChain() {
        assertNull(KeystoreFailurePolicy.keystoreErrorCode(UnrecoverableKeyException("no cause")))
        val nested = UnrecoverableKeyException("wrapped").initCause(ProviderException("provider", IllegalStateException("root")))
        assertNull(KeystoreFailurePolicy.keystoreErrorCode(nested))
    }
}
