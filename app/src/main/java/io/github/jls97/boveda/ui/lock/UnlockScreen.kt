package io.github.jls97.boveda.ui.lock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jls97.boveda.data.AntiPhishingPhrase
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.InsecureDeviceWarning
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.OpenLocalDocument
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.RestoreBackupDialog
import io.github.jls97.boveda.ui.components.SecureAlertDialog
import io.github.jls97.boveda.ui.components.findActivity
import io.github.jls97.boveda.ui.components.hasSecureLockScreen
import io.github.jls97.boveda.ui.components.readBackup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de desbloqueo. [requestContext] es, en el autorrelleno, para quién se va a rellenar
 * (p. ej. «Para: com.ejemplo.app»); se muestra bajo el título para que la pantalla no sea genérica
 * (M-04). En la app principal queda a null.
 *
 * [onUsePasswordInApp], solo en el autorrelleno: con la huella activada, «Usar contraseña» no
 * despliega el campo en esta pantalla, que vive dentro de la tarea de la app que pide el relleno,
 * sino que abre Bóveda desde su propia tarea y cierra esta (M-04). Así la contraseña maestra no se
 * teclea nunca encima de otra app mientras haya huella.
 */
@Composable
fun UnlockScreen(
    viewModel: LockViewModel,
    allowRestore: Boolean = true,
    requestContext: String? = null,
    onUsePasswordInApp: (() -> Unit)? = null,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    val biometricEnabled = remember { viewModel.isBiometricEnabled() }
    // Con huella, la contraseña maestra queda plegada: cuanto menos se teclee en una pantalla que
    // aparece encima de otra app, menos vale imitarla (M-04).
    var usePassword by remember { mutableStateOf(!biometricEnabled) }
    // Vive fuera de la bóveda cifrada: hay que enseñarla antes de abrirla (M-04).
    val phrase = remember { AntiPhishingPhrase(context.applicationContext).phrase.value }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmRestore by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<ByteArray?>(null) }
    // Copia y su contraseña a la espera de la confirmación fuerte de restaurar sin la actual (B-31).
    var forcedRestore by remember { mutableStateOf<ForcedRestore?>(null) }
    var confirmUndo by remember { mutableStateOf(false) }
    // Se comprueba cada vez que la pantalla vuelve al frente: el usuario puede haber quitado el PIN.
    val resumeTick by viewModel.resumeTicks.collectAsStateWithLifecycle()
    val deviceSecure = remember(resumeTick) { context.hasSecureLockScreen() }
    // Tras cada operación (restaurar, deshacer) puede haber cambiado.
    val canUndoRestore = remember(resumeTick, ui.busy) { allowRestore && viewModel.canUndoRestore() }

    val blocked = ui.blockedUntil > now
    LaunchedEffect(ui.blockedUntil) {
        while (ui.blockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }

    /** «Usar contraseña»: en la app, despliega el campo; en el autorrelleno, abre Bóveda (M-04). */
    fun usePasswordInstead() {
        if (onUsePasswordInApp != null) onUsePasswordInApp() else usePassword = true
    }

    fun promptFingerprint() {
        val activity = context.findActivity() ?: return
        val cipher = viewModel.biometricCipher()
        if (cipher == null) {
            viewModel.showError(
                "La huella ya no es válida (¿has añadido otra huella al teléfono?). " +
                    "Entra con tu contraseña y vuelve a activarla en Ajustes.",
            )
            return
        }
        // Con el campo plegado el botón negativo promete la contraseña y debe cumplirlo; con el
        // campo a la vista solo puede ser «Cancelar». Cerrar el diálogo (atrás) no cambia nada.
        BiometricPrompts.authenticate(
            activity,
            "Desbloquear Bóveda",
            "Confirma con tu huella",
            cipher,
            negativeLabel = if (usePassword) "Cancelar" else "Usar contraseña",
            onNegative = { if (!usePassword) usePasswordInstead() },
        ) { authorized, error ->
            when {
                authorized != null -> viewModel.unlockWithBiometric(authorized)
                error != null -> viewModel.showError(error)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (biometricEnabled) {
            delay(300)
            promptFingerprint()
        }
    }

    val openBackup = rememberLauncherForActivityResult(OpenLocalDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = readBackup(context.contentResolver, uri)
                if (bytes == null) viewModel.showError("No se pudo leer el archivo.") else pendingBackup = bytes
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize().testTag("unlock_screen")) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Bóveda", style = MaterialTheme.typography.displaySmall)
            Text("Bloqueada", style = MaterialTheme.typography.titleMedium)
            if (requestContext != null) {
                Text(requestContext, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AntiPhishingBanner(phrase)
            if (!deviceSecure) InsecureDeviceWarning()
            if (usePassword) {
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Contraseña maestra",
                    imeAction = ImeAction.Done,
                    onImeAction = { if (!blocked) viewModel.unlock(password) },
                    enabled = !ui.busy && !blocked,
                )
            }
            if (blocked) {
                val seconds = (ui.blockedUntil - now + 999) / 1_000
                Text(
                    "Demasiados intentos fallidos. Vuelve a intentarlo en $seconds s.",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                ui.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Descifrando…")
                }
            } else {
                if (usePassword) {
                    Button(
                        onClick = { viewModel.unlock(password) },
                        enabled = password.isNotEmpty() && !blocked,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Desbloquear")
                    }
                }
                if (biometricEnabled) {
                    if (usePassword) {
                        OutlinedButton(onClick = { promptFingerprint() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Usar huella")
                        }
                    } else {
                        Button(onClick = { promptFingerprint() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Usar huella")
                        }
                        TextButton(onClick = { usePasswordInstead() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Usar contraseña")
                        }
                        if (onUsePasswordInApp != null) {
                            Text(
                                "Se abrirá Bóveda: la contraseña maestra no se escribe en esta pantalla, que " +
                                    "aparece encima de otra app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (allowRestore) {
                HorizontalDivider()
                TextButton(onClick = { confirmRestore = true }, enabled = !ui.busy) {
                    Text("Restaurar una copia de seguridad")
                }
                if (canUndoRestore) {
                    TextButton(onClick = { confirmUndo = true }, enabled = !ui.busy) {
                        Text("Volver a la bóveda anterior")
                    }
                }
            }
        }
    }

    if (confirmUndo) {
        ConfirmDialog(
            title = "¿Volver a la bóveda anterior?",
            text = "La bóveda de antes de la última restauración volverá a su sitio, bloqueada: se abre con " +
                "su propia contraseña maestra y la huella quedará desactivada. La bóveda de ahora se guarda " +
                "en su lugar, así que podrás volver a cambiar.",
            confirmLabel = "Deshacer",
            onConfirm = {
                confirmUndo = false
                viewModel.undoRestore()
            },
            onDismiss = { confirmUndo = false },
        )
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "¿Restaurar una copia?",
            text = "La bóveda de este teléfono se sustituirá por la de la copia y se perderá lo que no " +
                "esté en ella. Úsalo si la bóveda no se puede abrir o si vienes de otro teléfono.",
            confirmLabel = "Elegir archivo",
            onConfirm = {
                confirmRestore = false
                viewModel.expectExternalActivity()
                openBackup.launch(arrayOf("*/*"))
            },
            onDismiss = { confirmRestore = false },
        )
    }

    // Con bóveda en el teléfono hace falta también su contraseña maestra actual, o confirmar
    // expresamente que se restaura sin ella (B-31). Basta la actual aunque la bóveda ya no se
    // pueda abrir en este teléfono: en ese caso no se comprueba.
    pendingBackup?.let { backup ->
        RestoreBackupDialog(
            text = "Escribe la contraseña maestra con la que se hizo la copia y la contraseña maestra " +
                "actual de la bóveda de este teléfono. Al terminar, la contraseña maestra será la de la copia.",
            onConfirm = { backupPassword, currentPassword ->
                pendingBackup = null
                viewModel.restoreBackup(backup, backupPassword, currentPassword, forceWithoutCurrent = false)
            },
            onDismiss = { pendingBackup = null },
            onForgotCurrent = { backupPassword ->
                pendingBackup = null
                forcedRestore = ForcedRestore(backup, backupPassword)
            },
        )
    }

    forcedRestore?.let { pending ->
        ForcedRestoreDialog(
            onConfirm = {
                forcedRestore = null
                viewModel.restoreBackup(pending.backup, pending.backupPassword, currentPassword = null, forceWithoutCurrent = true)
            },
            onDismiss = { forcedRestore = null },
        )
    }
}

/** Copia elegida y su contraseña, a la espera de la confirmación fuerte de restaurar sin la actual. */
private class ForcedRestore(val backup: ByteArray, val backupPassword: String)

/**
 * Confirmación fuerte de restaurar sin la contraseña maestra actual (B-31): hay que teclear la
 * palabra [LockViewModel.FORCED_RESTORE_WORD] y esperar [LockViewModel.FORCED_RESTORE_DELAY_SECONDS]
 * segundos, para que no baste con pulsar sin leer.
 */
@Composable
private fun ForcedRestoreDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableIntStateOf(LockViewModel.FORCED_RESTORE_DELAY_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    val confirmed = LockViewModel.forcedRestoreConfirmed(typed, secondsLeft)
    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Restaurar sin la contraseña actual?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "La bóveda de este teléfono se sustituirá sin comprobar que es tuya. Se guarda una copia " +
                        "para poder deshacerlo, pero el freno de intentos fallidos no se reinicia y la " +
                        "contraseña maestra pasará a ser la de la copia. Si solo has olvidado la contraseña, " +
                        "la bóveda que se va seguirá sin poder abrirse.",
                )
                NoLearningTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = "Escribe ${LockViewModel.FORCED_RESTORE_WORD} para confirmar",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmed) {
                Text(if (secondsLeft > 0) "Restaurar ($secondsLeft s)" else "Restaurar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * La frase antiphishing, bien visible, encima de la contraseña (M-04). Sin frase (bóvedas creadas
 * antes de existir), un aviso para elegirla en Ajustes: la pantalla sigue siendo genérica hasta
 * entonces.
 */
@Composable
private fun AntiPhishingBanner(phrase: String?) {
    if (phrase == null) {
        Text(
            "Esta bóveda no tiene frase antiphishing. Elígela en Ajustes → Seguridad: Bóveda la mostrará " +
                "siempre aquí y, si una app imita esta pantalla, no la conocerá.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Tu frase antiphishing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                phrase,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Si no la ves, no escribas la contraseña.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
