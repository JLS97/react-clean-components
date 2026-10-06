package io.github.jls97.boveda.session

import io.github.jls97.boveda.session.RestorePolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePolicyTest {

    @Test
    fun withoutAVaultNothingIsChecked() {
        for (unlocked in listOf(false, true)) {
            for (given in listOf(false, true)) {
                for (force in listOf(false, true)) {
                    assertEquals(
                        "sin bóveda (unlocked=$unlocked, given=$given, force=$force)",
                        Decision.NO_VAULT,
                        RestorePolicy.decide(vaultOnPhone = false, unlocked, given, force),
                    )
                }
            }
        }
    }

    @Test
    fun unlockedVaultAlwaysNeedsTheCurrentPassword() {
        assertEquals(Decision.VERIFY_CURRENT, RestorePolicy.decide(true, unlocked = true, currentPasswordGiven = true, forceWithoutCurrent = false))
        assertEquals(Decision.VERIFY_CURRENT, RestorePolicy.decide(true, unlocked = true, currentPasswordGiven = true, forceWithoutCurrent = true))
        assertEquals("sin contraseña", Decision.REFUSE, RestorePolicy.decide(true, unlocked = true, currentPasswordGiven = false, forceWithoutCurrent = false))
        assertEquals("forzar no vale con la bóveda abierta", Decision.REFUSE, RestorePolicy.decide(true, unlocked = true, currentPasswordGiven = false, forceWithoutCurrent = true))
    }

    @Test
    fun lockedVaultChecksThePasswordWhenGivenAndOnlyForcesExplicitly() {
        assertEquals(Decision.VERIFY_CURRENT, RestorePolicy.decide(true, unlocked = false, currentPasswordGiven = true, forceWithoutCurrent = false))
        assertEquals("la contraseña dada manda sobre forzar", Decision.VERIFY_CURRENT, RestorePolicy.decide(true, unlocked = false, currentPasswordGiven = true, forceWithoutCurrent = true))
        assertEquals("la sobrecarga compatible (null, sin forzar) se rechaza", Decision.REFUSE, RestorePolicy.decide(true, unlocked = false, currentPasswordGiven = false, forceWithoutCurrent = false))
        assertEquals(Decision.FORCED, RestorePolicy.decide(true, unlocked = false, currentPasswordGiven = false, forceWithoutCurrent = true))
    }

    @Test
    fun onlyAForcedRestoreKeepsTheThrottle() {
        assertTrue(RestorePolicy.resetsThrottle(Decision.NO_VAULT))
        assertTrue(RestorePolicy.resetsThrottle(Decision.VERIFY_CURRENT))
        assertFalse(RestorePolicy.resetsThrottle(Decision.FORCED))
    }

    @Test
    fun masterPasswordWarningOnlyWhenAVaultWasReplacedByAnotherPassword() {
        assertFalse("sin bóveda previa", RestorePolicy.masterPasswordChanges(vaultOnPhone = false, currentPasswordGiven = false, samePassword = false))
        assertFalse("sin bóveda previa", RestorePolicy.masterPasswordChanges(vaultOnPhone = false, currentPasswordGiven = true, samePassword = true))
        assertFalse("misma contraseña comprobada", RestorePolicy.masterPasswordChanges(vaultOnPhone = true, currentPasswordGiven = true, samePassword = true))
        assertTrue("otra contraseña", RestorePolicy.masterPasswordChanges(vaultOnPhone = true, currentPasswordGiven = true, samePassword = false))
        assertTrue("sin saberlo se avisa", RestorePolicy.masterPasswordChanges(vaultOnPhone = true, currentPasswordGiven = false, samePassword = false))
        assertTrue("samePassword no cuenta sin contraseña actual", RestorePolicy.masterPasswordChanges(vaultOnPhone = true, currentPasswordGiven = false, samePassword = true))
    }
}
