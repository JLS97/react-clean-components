package io.github.jls97.boveda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Cuándo recuerda la lista principal que toca hacer copia de seguridad (B-38). */
class BackupReminderTest {

    private val now = 1_800_000_000_000L
    private val recent = BackupStatus(lastBackupAt = now - TimeUnit.DAYS.toMillis(3), changesSince = 2)

    @Test
    fun neverBackedUpAlwaysReminds() {
        val text = backupReminder(BackupStatus(), now)
        assertNotNull(text)
        assertTrue(text!!.contains("ninguna copia"))
    }

    @Test
    fun existingVaultWithoutRecordGetsTheSofterNotice() {
        // Bóveda anterior al registro de copias: no se sabe si hubo copia, así que no se afirma «nunca».
        val unknown = BackupStatus(historyUnknown = true)
        assertFalse(unknown.neverBackedUp)
        assertTrue(unknown.unverifiedHistory)
        assertEquals(0L, unknown.daysSinceBackup(now))
        val text = backupReminder(unknown, now)
        assertNotNull(text)
        assertTrue(text!!.contains("No consta ninguna copia verificada"))
        assertFalse(text.contains("Todavía no hay"))
        assertEquals("Última copia verificada con esta versión: ninguna.", lastBackupLabel(unknown) { "FECHA" })
        // Un motivo concreto sigue ganando, y la primera copia verificada cierra la incógnita.
        assertTrue(backupReminder(unknown.copy(pendingReason = "Motivo."), now)!!.startsWith("Motivo."))
        val verified = unknown.copy(lastBackupAt = now - TimeUnit.DAYS.toMillis(1))
        assertFalse(verified.unverifiedHistory)
        assertNull(backupReminder(verified, now))
        assertEquals("Última copia: FECHA. Sin cambios desde entonces.", lastBackupLabel(verified) { "FECHA" })
    }

    @Test
    fun recentBackupWithFewChangesIsQuiet() {
        assertNull(backupReminder(recent, now))
        assertNull(backupReminder(recent.copy(changesSince = BACKUP_REMINDER_CHANGES), now))
        assertNull(backupReminder(recent.copy(lastBackupAt = now - TimeUnit.DAYS.toMillis(BACKUP_REMINDER_DAYS.toLong())), now))
    }

    @Test
    fun oldBackupReminds() {
        val old = recent.copy(lastBackupAt = now - TimeUnit.DAYS.toMillis(47), changesSince = 12)
        val text = backupReminder(old, now)
        assertNotNull(text)
        assertTrue(text!!.contains("47 días"))
        assertTrue(text.contains("12 cambios"))
    }

    @Test
    fun manyChangesRemind() {
        val text = backupReminder(recent.copy(changesSince = BACKUP_REMINDER_CHANGES + 1), now)
        assertNotNull(text)
        assertTrue(text!!.contains("${BACKUP_REMINDER_CHANGES + 1} cambios"))
    }

    @Test
    fun pendingReasonWinsAndAsksForABackupNow() {
        val reason = "Has guardado tu primer código 2FA."
        val text = backupReminder(recent.copy(pendingReason = reason), now)
        assertNotNull(text)
        assertTrue(text!!.startsWith(reason))
        assertTrue(text.contains("ahora"))
        // También cuando nunca se hizo copia: el motivo concreto es más útil que el aviso genérico.
        assertTrue(backupReminder(BackupStatus(pendingReason = reason), now)!!.startsWith(reason))
    }

    @Test
    fun clockGoingBackwardsDoesNotCountNegativeDays() {
        val future = recent.copy(lastBackupAt = now + TimeUnit.DAYS.toMillis(2))
        assertEquals(0L, future.daysSinceBackup(now))
        assertNull(backupReminder(future, now))
    }

    @Test
    fun settingsLabelShowsDateAndChanges() {
        val format: (Long) -> String = { "FECHA" }
        assertEquals("Última copia: nunca.", lastBackupLabel(BackupStatus(), format))
        assertEquals("Última copia: FECHA. Sin cambios desde entonces.", lastBackupLabel(recent.copy(changesSince = 0), format))
        assertEquals("Última copia: FECHA. 1 cambio sin copiar desde entonces.", lastBackupLabel(recent.copy(changesSince = 1), format))
        assertEquals("Última copia: FECHA. 2 cambios sin copiar desde entonces.", lastBackupLabel(recent, format))
    }
}
