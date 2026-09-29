package io.github.jls97.boveda.ui.components

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.core.generator.PasswordStrength
import io.github.jls97.boveda.core.generator.StrengthLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date

/** Password input that hides its content until "Mostrar" is tapped. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onAny = { if (onImeAction != null) onImeAction() else defaultKeyboardAction(imeAction) },
        ),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "Ocultar" else "Mostrar")
            }
        },
    )
}

@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return
    val level = PasswordStrength.level(PasswordStrength.estimateBits(password))
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { (level.ordinal + 1) / StrengthLevel.entries.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = strengthColor(level),
        )
        Text(
            text = "Fortaleza: ${strengthLabel(level)}",
            style = MaterialTheme.typography.bodySmall,
            color = strengthColor(level),
        )
    }
}

fun strengthLabel(level: StrengthLevel): String = when (level) {
    StrengthLevel.VERY_WEAK -> "muy débil"
    StrengthLevel.WEAK -> "débil"
    StrengthLevel.FAIR -> "aceptable"
    StrengthLevel.STRONG -> "fuerte"
    StrengthLevel.VERY_STRONG -> "muy fuerte"
}

@Composable
fun strengthColor(level: StrengthLevel): Color = when (level) {
    StrengthLevel.VERY_WEAK, StrengthLevel.WEAK -> MaterialTheme.colorScheme.error
    StrengthLevel.FAIR -> MaterialTheme.colorScheme.tertiary
    StrengthLevel.STRONG, StrengthLevel.VERY_STRONG -> MaterialTheme.colorScheme.primary
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Dialog that asks for one password, for example the one of a backup file. */
@Composable
fun PasswordPromptDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text)
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Contraseña maestra de la copia",
                    imeAction = ImeAction.Done,
                    onImeAction = { if (password.isNotEmpty()) onConfirm(password) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Single-choice list in a dialog (auto-lock time, clipboard time...). */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = value == selected,
                                onClick = { onSelect(value) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

fun autoLockLabel(seconds: Int): String = when {
    seconds == 0 -> "Al salir de la app"
    seconds < 60 -> "$seconds segundos"
    seconds == 60 -> "1 minuto"
    else -> "${seconds / 60} minutos"
}

fun durationLabel(seconds: Int): String = if (seconds < 60) "$seconds segundos" else autoLockLabel(seconds)

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Backups are small; anything bigger than this is not a vault file. */
private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

suspend fun readBackup(resolver: ContentResolver, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    resolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(8_192)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            output.write(chunk, 0, read)
            if (output.size() > MAX_BACKUP_BYTES) return@use null
        }
        output.toByteArray()
    }
}

suspend fun writeBackup(resolver: ContentResolver, uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
    resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
}
