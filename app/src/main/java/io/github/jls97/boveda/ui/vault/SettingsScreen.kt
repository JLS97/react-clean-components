package io.github.jls97.boveda.ui.vault

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jls97.boveda.core.vault.VaultSettings
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.session.OtpAccess
import io.github.jls97.boveda.ui.components.BackButton
import io.github.jls97.boveda.ui.components.ChoiceDialog
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.CreateLocalDocument
import io.github.jls97.boveda.ui.components.OpenLocalDocument
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.PasswordPromptDialog
import io.github.jls97.boveda.ui.components.StrengthMeter
import io.github.jls97.boveda.ui.components.autoLockLabel
import io.github.jls97.boveda.ui.components.durationLabel
import io.github.jls97.boveda.ui.components.findActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: VaultSettings,
    biometricEnabled: Boolean,
    entryCount: Int,
    otpAccess: OtpAccess,
    otpCount: Int,
    onRecoverOtp: () -> Unit,
    onNewRecoveryCode: () -> Unit,
    viewModel: VaultViewModel,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    var choosingAutoLock by remember { mutableStateOf(false) }
    var choosingClipboard by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val biometricAvailable = remember { BiometricPrompts.isStrongBiometricAvailable(context) }
    val resumeTick by viewModel.resumeTicks.collectAsStateWithLifecycle()
    val autofillEnabled = remember(resumeTick) {
        context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
    }

    fun openAutofillSettings() {
        if (autofillEnabled) {
            viewModel.message(
                "Bóveda ya es tu servicio de autorrelleno. Para cambiarlo, busca «Servicio de autocompletar» " +
                    "en los ajustes del teléfono.",
            )
            return
        }
        viewModel.expectExternalActivity()
        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
            .setData(Uri.parse("package:${context.packageName}"))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            viewModel.message("Actívalo en los ajustes del teléfono: busca «Servicio de autocompletar».")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(CreateLocalDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(OpenLocalDocument()) { uri ->
        restoreUri = uri
    }

    fun setBiometric(enable: Boolean) {
        if (!enable) {
            viewModel.disableBiometric()
            return
        }
        val activity = context.findActivity() ?: return
        val cipher = viewModel.biometricEnrollmentCipher()
        if (cipher == null) {
            viewModel.message("No se pudo preparar la huella. Comprueba que tienes una registrada.")
            return
        }
        BiometricPrompts.authenticate(activity, "Activar huella", "Confirma con tu huella", cipher) { authorized, error ->
            when {
                authorized != null -> viewModel.enableBiometric(authorized)
                error != null -> viewModel.message(error)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = { BackButton { viewModel.back() } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Seguridad")
            ListItem(
                headlineContent = { Text("Bloqueo automático") },
                supportingContent = { Text(autoLockLabel(settings.autoLockSeconds) + ". Siempre al apagar la pantalla.") },
                modifier = Modifier.clickable(enabled = !viewModel.busy) { choosingAutoLock = true },
            )
            ListItem(
                headlineContent = { Text("Borrar portapapeles") },
                supportingContent = { Text("A los ${durationLabel(settings.clipboardClearSeconds)} de copiar") },
                modifier = Modifier.clickable(enabled = !viewModel.busy) { choosingClipboard = true },
            )
            ListItem(
                headlineContent = { Text("Desbloqueo con huella") },
                supportingContent = {
                    Text(
                        if (biometricAvailable) {
                            "La clave solo se libera con una huella fuerte y se invalida si añades otra."
                        } else {
                            "No hay ninguna huella segura registrada en el teléfono."
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { setBiometric(it) },
                        enabled = !viewModel.busy && (biometricAvailable || biometricEnabled),
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Cambiar contraseña maestra") },
                modifier = Modifier.clickable(enabled = !viewModel.busy) { changingPassword = true },
            )
            HorizontalDivider()

            SectionTitle("Autorrelleno")
            ListItem(
                headlineContent = { Text("Rellenar en otras apps y en Chrome") },
                supportingContent = {
                    Text(
                        if (autofillEnabled) {
                            "Activado. Al tocar un campo de usuario o contraseña aparecerá «Bóveda» en el teclado."
                        } else {
                            "Desactivado. Toca aquí para elegir Bóveda como servicio de autorrelleno."
                        },
                    )
                },
                trailingContent = { Switch(checked = autofillEnabled, onCheckedChange = { openAutofillSettings() }) },
                modifier = Modifier.clickable { openAutofillSettings() },
            )
            Text(
                "En Chrome, además: Ajustes → Servicios de autocompletar → «Autocompletar con otro servicio». " +
                    "El teclado solo ve la palabra «Bóveda»: la cuenta la eliges dentro de la app, con la huella.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))

            SectionTitle("Códigos 2FA")
            ListItem(
                headlineContent = {
                    Text(
                        when (otpAccess) {
                            OtpAccess.NONE -> "Todavía no hay ninguno"
                            OtpAccess.READY -> if (otpCount == 1) "1 código" else "$otpCount códigos"
                            OtpAccess.LOCKED -> "Bloqueados en este móvil"
                        },
                    )
                },
                supportingContent = {
                    Text(
                        when (otpAccess) {
                            OtpAccess.NONE -> "Se añaden desde cada entrada. Cada código se abrirá solo con tu huella."
                            OtpAccess.READY -> "Cada uno se abre solo con tu huella, aunque la bóveda esté desbloqueada."
                            OtpAccess.LOCKED ->
                                "Este móvil no tiene la llave de huella que los abre (copia restaurada o huellas " +
                                    "cambiadas). Recupéralos con tu código de recuperación."
                        },
                    )
                },
            )
            if (otpAccess == OtpAccess.LOCKED) {
                ListItem(
                    headlineContent = { Text("Recuperar con el código de recuperación") },
                    modifier = Modifier.clickable(enabled = !viewModel.busy) { onRecoverOtp() },
                )
            }
            if (otpAccess == OtpAccess.READY) {
                ListItem(
                    headlineContent = { Text("Nuevo código de recuperación") },
                    supportingContent = { Text("Si has perdido el papel donde lo apuntaste o alguien lo ha visto.") },
                    modifier = Modifier.clickable(enabled = !viewModel.busy) { onNewRecoveryCode() },
                )
            }
            HorizontalDivider()

            SectionTitle("Copias de seguridad")
            Text(
                "La copia es un archivo cifrado con tu contraseña maestra actual. Solo se puede guardar en " +
                    "el almacenamiento del teléfono o en un USB conectado, nunca en la nube. Pásala después " +
                    "a un USB o a un ordenador: si pierdes el móvil, es la única forma de recuperar tus " +
                    "$entryCount entradas. Los códigos 2FA van dentro, cifrados: para abrirlos en otro móvil " +
                    "hará falta también tu código de recuperación.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ListItem(
                headlineContent = { Text("Exportar copia cifrada") },
                modifier = Modifier.clickable(enabled = !viewModel.busy) {
                    viewModel.expectExternalActivity()
                    val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
                    exportLauncher.launch("boveda-$date.bvd")
                },
            )
            ListItem(
                headlineContent = { Text("Restaurar copia") },
                supportingContent = { Text("Sustituye todo el contenido actual por el de la copia.") },
                modifier = Modifier.clickable(enabled = !viewModel.busy) { confirmRestore = true },
            )
            HorizontalDivider()

            SectionTitle("Privacidad")
            Text(
                "• Sin permiso de Internet: Android no deja que la app abra ninguna conexión.\n" +
                    "• Cifrado AES-256-GCM con clave derivada por Argon2id (64 MiB, 3 pasadas).\n" +
                    "• Capa extra ligada al chip de seguridad del teléfono (Android Keystore).\n" +
                    "• Sin capturas de pantalla, sin copias en la nube y sin autorrelleno de terceros.\n" +
                    "• Consejo: desactiva la sincronización del portapapeles del teclado y de HyperOS.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            Button(
                onClick = { viewModel.lock() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Bloquear ahora")
            }
        }
    }

    if (choosingAutoLock) {
        ChoiceDialog(
            title = "Bloqueo automático",
            options = VaultSettings.AUTO_LOCK_CHOICES.map { it to autoLockLabel(it) },
            selected = settings.autoLockSeconds,
            onSelect = {
                choosingAutoLock = false
                viewModel.setAutoLock(it)
            },
            onDismiss = { choosingAutoLock = false },
        )
    }

    if (choosingClipboard) {
        ChoiceDialog(
            title = "Borrar portapapeles",
            options = VaultSettings.CLIPBOARD_CLEAR_CHOICES.map { it to durationLabel(it) },
            selected = settings.clipboardClearSeconds,
            onSelect = {
                choosingClipboard = false
                viewModel.setClipboardClear(it)
            },
            onDismiss = { choosingClipboard = false },
        )
    }

    if (changingPassword) {
        ChangePasswordDialog(
            busy = viewModel.busy,
            onConfirm = { current, newPassword, confirmation ->
                viewModel.changeMasterPassword(current, newPassword, confirmation) { changingPassword = false }
            },
            onDismiss = { changingPassword = false },
        )
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "¿Restaurar una copia?",
            text = "Todo lo que hay ahora en la bóveda se sustituirá por el contenido de la copia.",
            confirmLabel = "Elegir archivo",
            onConfirm = {
                confirmRestore = false
                viewModel.expectExternalActivity()
                restoreLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { confirmRestore = false },
        )
    }

    restoreUri?.let { uri ->
        PasswordPromptDialog(
            title = "Restaurar copia",
            text = "Escribe la contraseña maestra con la que se hizo la copia.",
            confirmLabel = "Restaurar",
            onConfirm = { password ->
                restoreUri = null
                viewModel.restoreBackup(uri, password)
            },
            onDismiss = { restoreUri = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ChangePasswordDialog(
    busy: Boolean,
    onConfirm: (current: String, newPassword: String, confirmation: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Cambiar contraseña maestra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordField(value = current, onValueChange = { current = it }, label = "Contraseña actual", enabled = !busy)
                PasswordField(value = newPassword, onValueChange = { newPassword = it }, label = "Nueva contraseña", enabled = !busy)
                StrengthMeter(newPassword)
                PasswordField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = "Repite la nueva",
                    imeAction = ImeAction.Done,
                    enabled = !busy,
                )
                if (busy) Text("Cifrando…", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(current, newPassword, confirmation) },
                enabled = !busy && current.isNotEmpty() && newPassword.isNotEmpty() && confirmation.isNotEmpty(),
            ) {
                Text("Cambiar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}
