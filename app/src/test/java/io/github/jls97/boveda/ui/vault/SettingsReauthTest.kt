package io.github.jls97.boveda.ui.vault

import io.github.jls97.boveda.core.vault.VaultSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué cambios de ajustes exigen volver a escribir la contraseña maestra (B-37). */
class SettingsReauthTest {

    private val current = VaultSettings(autoLockSeconds = 60, clipboardClearSeconds = 30)

    @Test
    fun longerAutoLockRelaxesSecurity() {
        assertTrue(relaxesSecurity(current, current.copy(autoLockSeconds = 300)))
        assertTrue(relaxesSecurity(current, current.copy(autoLockSeconds = 900)))
    }

    @Test
    fun shorterOrEqualAutoLockDoesNot() {
        assertFalse(relaxesSecurity(current, current.copy(autoLockSeconds = 30)))
        assertFalse(relaxesSecurity(current, current.copy(autoLockSeconds = 60)))
    }

    @Test
    fun lockOnLeavingTheAppIsTheStrictest() {
        assertFalse(relaxesSecurity(current, current.copy(autoLockSeconds = 0)))
        val strictest = current.copy(autoLockSeconds = 0)
        for (seconds in VaultSettings.AUTO_LOCK_CHOICES.filter { it > 0 }) {
            assertTrue("$seconds s", relaxesSecurity(strictest, strictest.copy(autoLockSeconds = seconds)))
        }
    }

    @Test
    fun longerClipboardRelaxesSecurity() {
        assertTrue(relaxesSecurity(current, current.copy(clipboardClearSeconds = 60)))
        assertTrue(relaxesSecurity(current, current.copy(clipboardClearSeconds = 120)))
        assertFalse(relaxesSecurity(current, current.copy(clipboardClearSeconds = 15)))
        assertFalse(relaxesSecurity(current, current.copy(clipboardClearSeconds = 30)))
    }

    @Test
    fun unchangedSettingsDoNotAskForThePassword() {
        assertFalse(relaxesSecurity(current, current.copy()))
    }
}
