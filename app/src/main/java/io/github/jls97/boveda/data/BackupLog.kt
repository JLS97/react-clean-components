package io.github.jls97.boveda.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Lo que se sabe de la última copia de seguridad verificada. No contiene ningún secreto: solo una
 * fecha, un contador y, si procede, el motivo por el que conviene hacer copia ya.
 */
data class BackupStatus(
    /** Época en milisegundos de la última copia verificada, o 0 si nunca se hizo ninguna. */
    val lastBackupAt: Long = 0L,
    /** Guardados y borrados desde esa copia. */
    val changesSince: Int = 0,
    /** Cambio importante (primer código 2FA, contraseña maestra...) sin copia posterior, o null. */
    val pendingReason: String? = null,
) {
    val neverBackedUp: Boolean get() = lastBackupAt == 0L

    fun daysSinceBackup(nowMillis: Long): Long =
        if (neverBackedUp) 0L else TimeUnit.MILLISECONDS.toDays((nowMillis - lastBackupAt).coerceAtLeast(0L))
}

/**
 * Registro de copias de seguridad en SharedPreferences, fuera de la bóveda cifrada (B-38). La copia
 * manual es la única vía de recuperación, así que la app apunta cuándo se hizo la última copia
 * verificada y cuántos cambios ha habido desde entonces para poder recordarlo.
 */
class BackupLog(context: Context) {
    private val prefs = context.getSharedPreferences("backup_log", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(read())

    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    /** Un guardado o un borrado más sin copia que lo recoja. */
    fun recordChange() {
        prefs.edit().putInt(KEY_CHANGES, _status.value.changesSince + 1).apply()
        refresh()
    }

    /** Una copia escrita, releída y verificada: a partir de aquí se cuenta de cero. */
    fun recordVerifiedBackup(atMillis: Long) {
        prefs.edit()
            .putLong(KEY_LAST_BACKUP, atMillis)
            .putInt(KEY_CHANGES, 0)
            .remove(KEY_REASON)
            .apply()
        refresh()
    }

    /** Un cambio que deja obsoletas las copias anteriores: se recordará hasta la siguiente copia. */
    fun requestBackup(reason: String) {
        prefs.edit().putString(KEY_REASON, reason).apply()
        refresh()
    }

    private fun refresh() {
        _status.value = read()
    }

    private fun read() = BackupStatus(
        lastBackupAt = prefs.getLong(KEY_LAST_BACKUP, 0L),
        changesSince = prefs.getInt(KEY_CHANGES, 0),
        pendingReason = prefs.getString(KEY_REASON, null),
    )

    private companion object {
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_CHANGES = "changes_since"
        const val KEY_REASON = "pending_reason"
    }
}

/** Días sin copia a partir de los cuales la lista principal avisa. */
const val BACKUP_REMINDER_DAYS = 30

/** Cambios sin copiar a partir de los cuales la lista principal avisa. */
const val BACKUP_REMINDER_CHANGES = 10

/**
 * Aviso discreto para la lista principal, o null si no hace falta recordar nada: nunca se hizo
 * copia, hay un cambio importante pendiente, han pasado más de [BACKUP_REMINDER_DAYS] días o hay
 * más de [BACKUP_REMINDER_CHANGES] cambios sin copiar.
 */
fun backupReminder(status: BackupStatus, nowMillis: Long): String? {
    val days = status.daysSinceBackup(nowMillis)
    return when {
        status.pendingReason != null -> "${status.pendingReason} Haz una copia de seguridad ahora."
        status.neverBackedUp ->
            "Todavía no hay ninguna copia de seguridad. Si pierdes el móvil, pierdes la bóveda."
        days > BACKUP_REMINDER_DAYS ->
            "Última copia hace $days días" + (if (status.changesSince > 0) " y ${changesLabel(status.changesSince)}" else "") + "."
        status.changesSince > BACKUP_REMINDER_CHANGES -> "${changesLabel(status.changesSince).replaceFirstChar { it.uppercase() }}."
        else -> null
    }
}

/** Texto de Ajustes: fecha de la última copia verificada y cambios desde entonces. */
fun lastBackupLabel(status: BackupStatus, formatDate: (Long) -> String): String = when {
    status.neverBackedUp -> "Última copia: nunca."
    status.changesSince == 0 -> "Última copia: ${formatDate(status.lastBackupAt)}. Sin cambios desde entonces."
    else -> "Última copia: ${formatDate(status.lastBackupAt)}. ${changesLabel(status.changesSince).replaceFirstChar { it.uppercase() }} desde entonces."
}

private fun changesLabel(changes: Int): String =
    if (changes == 1) "1 cambio sin copiar" else "$changes cambios sin copiar"
