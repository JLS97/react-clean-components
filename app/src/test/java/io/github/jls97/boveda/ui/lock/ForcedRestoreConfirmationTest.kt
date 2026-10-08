package io.github.jls97.boveda.ui.lock

import io.github.jls97.boveda.session.OperationResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La confirmación fuerte de restaurar sin la contraseña actual y el aviso de restauración con bloqueo (B-31, R01-5). */
class ForcedRestoreConfirmationTest {

    @Test
    fun needsTheExactWordAndTheWholeWait() {
        assertTrue(LockViewModel.forcedRestoreConfirmed("RESTAURAR", secondsLeft = 0))
        assertTrue("espacios alrededor no cuentan", LockViewModel.forcedRestoreConfirmed(" RESTAURAR ", secondsLeft = 0))
        assertFalse("sin esperar", LockViewModel.forcedRestoreConfirmed("RESTAURAR", secondsLeft = 1))
        assertFalse("en minúsculas", LockViewModel.forcedRestoreConfirmed("restaurar", secondsLeft = 0))
        assertFalse("incompleta", LockViewModel.forcedRestoreConfirmed("RESTAURA", secondsLeft = 0))
        assertFalse("vacía", LockViewModel.forcedRestoreConfirmed("", secondsLeft = 0))
        assertFalse("con más texto", LockViewModel.forcedRestoreConfirmed("RESTAURAR ya", secondsLeft = 0))
    }

    @Test
    fun theWaitIsNotTrivial() {
        assertTrue(LockViewModel.FORCED_RESTORE_DELAY_SECONDS >= 3)
    }

    @Test
    fun restoredWhileLockedOnlyMentionsUndoWhenThereIsOne() {
        val withUndo = LockViewModel.restoredWhileLocked(OperationResult.Restored(masterPasswordChanged = true, hadUndo = true))
        val withoutUndo = LockViewModel.restoredWhileLocked(OperationResult.Restored(masterPasswordChanged = true, hadUndo = false))
        assertTrue(withUndo.contains("Copia restaurada"))
        assertTrue(withUndo.contains("deshacer"))
        assertTrue(withoutUndo.contains("Copia restaurada"))
        assertFalse(withoutUndo.contains("deshacer"))
    }
}
