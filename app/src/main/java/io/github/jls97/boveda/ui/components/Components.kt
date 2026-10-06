package io.github.jls97.boveda.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.DocumentsContract
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.core.autofill.CredentialMatcher
import io.github.jls97.boveda.core.generator.PasswordStrength
import io.github.jls97.boveda.core.generator.StrengthLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
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

/**
 * Campo de texto cuyo teclado no aprende ni sugiere lo escrito.
 *
 * Compose nunca pone IME_FLAG_NO_PERSONALIZED_LEARNING ni lo expone en KeyboardOptions, así que en
 * un campo normal (nombre, usuario, notas) el teclado añade lo tecleado a su diccionario personal y
 * puede sincronizarlo con la nube de su fabricante. Aquí se interceptan los EditorInfo que Compose
 * entrega al IME y se añaden los flags que lo evitan; el resto del campo es un OutlinedTextField.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoLearningTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
) {
    InterceptPlatformTextInput(interceptor = NoLearningInterceptor) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            keyboardOptions = keyboardOptions,
        )
    }
}

/** Añade a cada sesión del IME los flags de «sin aprendizaje» y «sin sugerencias». */
@OptIn(ExperimentalComposeUiApi::class)
private val NoLearningInterceptor = object : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession,
    ): Nothing = nextHandler.startInputMethod(
        object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                // Compose rellena outAttributes dentro de createInputConnection: los flags van después.
                val connection = request.createInputConnection(outAttributes)
                outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                outAttributes.inputType = outAttributes.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                return connection
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
    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Dialog that asks for one password, for example the one of a backup file or, con otro [label],
 * la contraseña maestra actual antes de una operación sensible.
 */
@Composable
fun PasswordPromptDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = "Contraseña maestra de la copia",
) {
    var password by remember { mutableStateOf("") }
    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text)
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = label,
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
    SecureAlertDialog(
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

/** "Web: banco.es" or "App: com.bank.app (firma 3a5f9c01…)" for a remembered autofill target. */
fun autofillTargetLabel(key: String): String = when {
    key.startsWith(CredentialMatcher.WEB_PREFIX) -> "Web: " + key.removePrefix(CredentialMatcher.WEB_PREFIX)
    key.startsWith(CredentialMatcher.APP_PREFIX) -> {
        val body = key.removePrefix(CredentialMatcher.APP_PREFIX)
        val packageName = body.substringBefore(CredentialMatcher.CERTIFICATE_SEPARATOR)
        val certificate = body.substringAfter(CredentialMatcher.CERTIFICATE_SEPARATOR, missingDelimiterValue = "")
        if (certificate.isEmpty()) "App: $packageName (sin firma, no se usa)" else "App: $packageName (firma ${certificate.take(8)}…)"
    }
    else -> key
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

/** True si el teléfono tiene un bloqueo de pantalla seguro (PIN, patrón o contraseña). */
fun Context.hasSecureLockScreen(): Boolean =
    getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/**
 * Aviso persistente para cuando el teléfono no tiene bloqueo de pantalla. La clave de hardware de
 * la bóveda exige «teléfono desbloqueado», pero sin PIN el teléfono cuenta siempre como
 * desbloqueado, así que esa capa deja de aportar nada y solo queda la contraseña maestra.
 */
@Composable
fun InsecureDeviceWarning(modifier: Modifier = Modifier) {
    Text(
        "Este teléfono no tiene bloqueo de pantalla (PIN, patrón o contraseña). La capa de hardware de " +
            "la bóveda solo protege con el teléfono bloqueado: ahora mismo cualquiera que lo coja llega " +
            "hasta aquí y solo le separa de tus datos la contraseña maestra. Activa un bloqueo en los " +
            "ajustes del teléfono.",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
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

/**
 * Escribe la copia truncando el archivo y la fuerza a disco (fsync) cuando el flujo lo permite,
 * para que desconectar un USB nada más terminar no la deje a medias (M-10). Devuelve false si el
 * proveedor no abre el archivo o la escritura falla; el que llama relee y verifica el archivo.
 */
suspend fun writeBackup(resolver: ContentResolver, uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
    try {
        val stream = resolver.openOutputStream(uri, "wt") ?: return@withContext false
        stream.use {
            it.write(bytes)
            it.flush()
            if (it is FileOutputStream) it.fd.sync()
        }
        true
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/**
 * Borra el documento que el selector del sistema ya creó cuando la copia no se pudo escribir o
 * verificar, para no dejar un .bvd vacío o dañado con nombre válido (M-10). Devuelve false si el
 * proveedor no lo permite; entonces se avisa al usuario para que lo borre a mano.
 */
suspend fun deleteDocument(resolver: ContentResolver, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        DocumentsContract.deleteDocument(resolver, uri)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        false
    }
}
