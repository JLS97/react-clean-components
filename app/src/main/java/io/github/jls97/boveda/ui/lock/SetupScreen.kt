package io.github.jls97.boveda.ui.lock

import android.app.KeyguardManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jls97.boveda.data.ANTI_PHISHING_MAX_LENGTH
import io.github.jls97.boveda.data.ANTI_PHISHING_MIN_LENGTH
import io.github.jls97.boveda.data.AntiPhishingPhrase
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.OpenLocalDocument
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.PasswordPromptDialog
import io.github.jls97.boveda.ui.components.StrengthMeter
import io.github.jls97.boveda.ui.components.readBackup
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(viewModel: LockViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    // Fuera de la bóveda: se enseña antes de desbloquear (M-04).
    val phrases = remember { AntiPhishingPhrase(context.applicationContext) }
    var pendingBackup by remember { mutableStateOf<ByteArray?>(null) }

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
            Text(
                "Crea tu contraseña maestra. Es la única llave de tus contraseñas: se usa para cifrarlas " +
                    "en este teléfono y no se guarda en ningún sitio. Si la olvidas, nadie puede recuperarla.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Consejo: una frase de 4 o 5 palabras que no estén relacionadas, con algún número o símbolo, " +
                    "es fácil de recordar y muy difícil de adivinar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Contraseña maestra",
                enabled = !ui.busy,
            )
            StrengthMeter(password)
            PasswordField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = "Repite la contraseña maestra",
                imeAction = ImeAction.Done,
                enabled = !ui.busy,
            )
            HorizontalDivider()
            Text("Tu frase antiphishing", style = MaterialTheme.typography.titleMedium)
            Text(
                "Elige una frase corta que solo tú conozcas. Bóveda la mostrará siempre antes de pedirte " +
                    "la contraseña maestra, también cuando rellene en otras apps. Una app que imite la " +
                    "pantalla de Bóveda no la conoce: si no ves tu frase, no escribas la contraseña. " +
                    "No es un secreto que cifre nada y podrás cambiarla en Ajustes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NoLearningTextField(
                value = phrase,
                onValueChange = { if (it.length <= ANTI_PHISHING_MAX_LENGTH) phrase = it },
                label = "Frase antiphishing ($ANTI_PHISHING_MIN_LENGTH-$ANTI_PHISHING_MAX_LENGTH caracteres)",
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                enabled = !ui.busy,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = understood, onCheckedChange = { understood = it }, enabled = !ui.busy)
                Text("Entiendo que si olvido la contraseña maestra perderé el acceso a mis datos.")
            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Cifrando la bóveda…")
                }
            } else {
                Button(
                    onClick = {
                        val keyguard = context.getSystemService(KeyguardManager::class.java)
                        if (keyguard?.isDeviceSecure != true) {
                            viewModel.showError(
                                "Activa antes un bloqueo de pantalla (PIN, patrón o contraseña) en los ajustes " +
                                    "del teléfono: la clave de hardware de la bóveda depende de él.",
                            )
                        } else {
                            viewModel.createVault(password, confirmation, phrase, phrases)
                        }
                    },
                    enabled = understood && password.isNotEmpty() && confirmation.isNotEmpty() && phrase.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Crear bóveda")
                }
            }
            HorizontalDivider()
            TextButton(
                onClick = {
                    viewModel.expectExternalActivity()
                    openBackup.launch(arrayOf("*/*"))
                },
                enabled = !ui.busy,
            ) {
                Text("Restaurar una copia de seguridad")
            }
        }
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
