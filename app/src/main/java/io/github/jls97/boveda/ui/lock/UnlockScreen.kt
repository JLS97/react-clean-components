package io.github.jls97.boveda.ui.lock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.OpenLocalDocument
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.PasswordPromptDialog
import io.github.jls97.boveda.ui.components.findActivity
import io.github.jls97.boveda.ui.components.readBackup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(viewModel: LockViewModel, allowRestore: Boolean = true) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    val biometricEnabled = remember { viewModel.isBiometricEnabled() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmRestore by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<ByteArray?>(null) }

    val blocked = ui.blockedUntil > now
    LaunchedEffect(ui.blockedUntil) {
        while (ui.blockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
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
        BiometricPrompts.authenticate(activity, "Desbloquear Bóveda", "Confirma con tu huella", cipher) { authorized, error ->
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

    Surface(modifier = Modifier.fillMaxSize()) {
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
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Contraseña maestra",
                imeAction = ImeAction.Done,
                onImeAction = { if (!blocked) viewModel.unlock(password) },
                enabled = !ui.busy && !blocked,
            )
            if (blocked) {
                val seconds = (ui.blockedUntil - now + 999) / 1_000
                Text(
                    "Demasiados intentos fallidos. Vuelve a intentarlo en $seconds s.",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Descifrando…")
                }
            } else {
                Button(
                    onClick = { viewModel.unlock(password) },
                    enabled = password.isNotEmpty() && !blocked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Desbloquear")
                }
                if (biometricEnabled) {
                    OutlinedButton(onClick = { promptFingerprint() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Usar huella")
                    }
                }
            }
            if (allowRestore) {
                HorizontalDivider()
                TextButton(onClick = { confirmRestore = true }, enabled = !ui.busy) {
                    Text("Restaurar una copia de seguridad")
                }
            }
        }
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

    pendingBackup?.let { backup ->
        PasswordPromptDialog(
            title = "Restaurar copia",
            text = "Escribe la contraseña maestra con la que se hizo la copia.",
            confirmLabel = "Restaurar",
            onConfirm = { backupPassword ->
                pendingBackup = null
                viewModel.restoreBackup(backup, backupPassword)
            },
            onDismiss = { pendingBackup = null },
        )
    }
}
