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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.vault.VaultSettings
import io.github.jls97.boveda.data.ANTI_PHISHING_MAX_LENGTH
import io.github.jls97.boveda.data.ANTI_PHISHING_MIN_LENGTH
import io.github.jls97.boveda.data.AntiPhishingPhrase
import io.github.jls97.boveda.data.antiPhishingPhraseProblem
import io.github.jls97.boveda.data.lastBackupLabel
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.session.OtpAccess
import io.github.jls97.boveda.ui.components.BackButton
import io.github.jls97.boveda.ui.components.ChoiceDialog
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.InsecureDeviceWarning
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.OpenLocalDocument
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.PasswordPromptDialog
import io.github.jls97.boveda.ui.components.RestoreBackupDialog
import io.github.jls97.boveda.ui.components.SecureAlertDialog
import io.github.jls97.boveda.ui.components.StrengthMeter
import io.github.jls97.boveda.ui.components.autoLockLabel
import io.github.jls97.boveda.ui.components.durationLabel
import io.github.jls97.boveda.ui.components.findActivity
import io.github.jls97.boveda.ui.components.formatDate
import io.github.jls97.boveda.ui.components.hasSecureLockScreen
import io.github.jls97.boveda.ui.components.kdfLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: VaultSettings,
    biometricEnabled: Boolean,
    /** Dónde vive la clave de Keystore que ata la bóveda a este teléfono (StrongBox, TEE, Software…). */
    deviceKeySecurityLevel: String,
    /** Aviso cuando esa clave es solo de software; null si no hay nada que avisar. */
    deviceKeyWarning: String?,
    entryCount: Int,
    otpAccess: OtpAccess,
    otpCount: Int,
    /** Parámetros Argon2id con los que está escrita la bóveda y, si no pudieron actualizarse, el aviso (I-09). */
    kdfParams: KdfParams,
    kdfUpgradeWarning: String?,
    onRecoverOtp: () -> Unit,
    onNewRecoveryCode: () -> Unit,
    onPickExportDestination: (String) -> Unit,
    /** Tras deshacer una restauración la bóveda queda bloqueada: avisa a la pantalla de bloqueo. */
    onRestoreUndone: () -> Unit,
    viewModel: VaultViewModel,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    var choosingAutoLock by remember { mutableStateOf(false) }
    var choosingClipboard by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    // Acción sobre la última restauración a la espera de confirmación (B-31).
    var undoAction by remember { mutableStateOf<UndoAction?>(null) }
    var confirmExport by remember { mutableStateOf(false) }
    var checkingRecoveryCode by remember { mutableStateOf(false) }
    var editingPhrase by remember { mutableStateOf(false) }
    // Operación sensible a la espera de la contraseña maestra (B-35, B-36, B-37).
    var reauth by remember { mutableStateOf<Reauth?>(null) }
    // Fuera de la bóveda cifrada: la pantalla de desbloqueo la enseña antes de abrirla (M-04).
    val phrases = remember { AntiPhishingPhrase(context.applicationContext) }
    val antiPhishingPhrase by phrases.phrase.collectAsStateWithLifecycle()
    val biometricAvailable = remember { BiometricPrompts.isStrongBiometricAvailable(context) }
    val resumeTick by viewModel.resumeTicks.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val deviceSecure = remember(resumeTick) { context.hasSecureLockScreen() }
    val autofillEnabled = remember(resumeTick) {
        context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
    }

    fun openAutofillSettings() {
        if (autofillEnabled) {
            viewModel.message(
                "Contraseñora ya es tu servicio de autorrelleno. Para cambiarlo, busca «Servicio de autocompletar» " +
                    "en los ajustes del teléfono.",
            )
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
            .setData(Uri.parse("package:${context.packageName}"))
        try {
            context.startActivity(intent)
            // Only once the system screen is really on its way: if it is not, "lock when leaving
            // the app" must keep working on the next exit.
            viewModel.expectExternalActivity()
        } catch (e: ActivityNotFoundException) {
            viewModel.message("Actívalo en los ajustes del teléfono: busca «Servicio de autocompletar».")
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(OpenLocalDocument()) { uri ->
        restoreUri = uri
    }

    /**
     * Sella la copia primero y solo después abre el selector de destino (M-10). El selector vive en la
     * raíz de la app, no en esta pantalla, así que sigue registrado aunque Ajustes salga de la
     * composición o la bóveda se bloquee mientras está abierto.
     */
    fun launchExport() {
        viewModel.prepareExport {
            val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
            viewModel.expectExternalActivity()
            onPickExportDestination("boveda-$date.bvd")
        }
    }

    val exportReauth = Reauth(
        title = "Exportar copia cifrada",
        text = "La copia sale del teléfono protegida solo por tu contraseña maestra. Escríbela para confirmar que eres tú.",
        confirmLabel = "Exportar",
        onVerified = ::launchExport,
    )

    /** Tras la confirmación: la huella si está activada (y su clave sigue válida); si no, la contraseña maestra. */
    fun authorizeExport() {
        val activity = context.findActivity()
        val cipher = if (biometricEnabled) viewModel.biometricUnlockCipher() else null
        if (activity == null || cipher == null) {
            reauth = exportReauth
            return
        }
        BiometricPrompts.authenticate(activity, "Exportar copia cifrada", "Confirma con tu huella", cipher) { authorized, error ->
            when {
                authorized != null -> launchExport()
                error != null -> viewModel.message(error)
                // «Usar contraseña» o cancelación: se ofrece la contraseña maestra.
                else -> reauth = exportReauth
            }
        }
    }

    /** Segundo paso de la activación: la contraseña maestra ya se ha comprobado. */
    fun enrollBiometric() {
        val activity = context.findActivity() ?: return
        val cipher = viewModel.biometricEnrollmentCipher()
        if (cipher == null) {
            viewModel.message("No se pudo preparar la huella. Comprueba que tienes una registrada.")
            return
        }
        // La contraseña maestra acaba de comprobarse: el botón negativo solo puede ser «Cancelar».
        BiometricPrompts.authenticate(activity, "Activar huella", "Confirma con tu huella", cipher, negativeLabel = "Cancelar") { authorized, error ->
            when {
                authorized != null -> viewModel.enableBiometric(authorized)
                error != null -> viewModel.message(error)
                // «Cancelar» o cierre del diálogo: la huella sigue desactivada y se dice.
                else -> viewModel.message("Activación cancelada. El desbloqueo con huella sigue desactivado.")
            }
        }
    }

    fun setBiometric(enable: Boolean) {
        if (!enable) {
            viewModel.disableBiometric()
            return
        }
        // Activarla es dar una llave permanente a cualquier dedo registrado en el teléfono: solo con la contraseña.
        reauth = Reauth(
            title = "Activar desbloqueo con huella",
            text = "Abrirá la bóveda cualquier huella ya registrada en este teléfono. Revisa las huellas en los " +
                "ajustes del sistema antes de activarla. La clave se destruye al inscribir una huella nueva o " +
                "quitar el bloqueo de pantalla. Escribe la contraseña maestra para confirmar que eres tú.",
            confirmLabel = "Continuar",
            onVerified = ::enrollBiometric,
        )
    }

    /** Aplica [proposed]; si deja la bóveda más expuesta que ahora, pide antes la contraseña maestra. */
    fun changeSettings(proposed: VaultSettings) {
        if (!relaxesSecurity(settings, proposed)) {
            viewModel.updateSettings(proposed)
            return
        }
        reauth = Reauth(
            title = "Relajar la seguridad",
            text = "Vas a dejar la bóveda o el portapapeles abiertos más tiempo que ahora. " +
                "Escribe la contraseña maestra para confirmar el cambio.",
            confirmLabel = "Cambiar",
            onVerified = { viewModel.updateSettings(proposed) },
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Ajustes") },
                    navigationIcon = { BackButton { viewModel.back() } },
                )
                // Comprobar la contraseña maestra tarda unos segundos: que se vea que algo está en marcha.
                if (viewModel.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Seguridad")
            if (!deviceSecure) InsecureDeviceWarning(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            // M-07: la alternancia StrongBox → TEE era silenciosa; ahora se ve qué protege la clave.
            ListItem(
                headlineContent = { Text("Clave de este teléfono") },
                supportingContent = {
                    Column {
                        Text(
                            "Protegida por $deviceKeySecurityLevel. Es la capa que impide abrir una copia de los " +
                                "archivos de la app fuera de este teléfono.",
                        )
                        if (deviceKeyWarning != null) {
                            Text(deviceKeyWarning, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
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
                            "Abrirá la bóveda cualquier huella ya registrada en este teléfono (solo huellas " +
                                "fuertes). Revisa las huellas en los ajustes del sistema antes de activarla. La " +
                                "clave se destruye al inscribir una huella nueva o quitar el bloqueo de pantalla. " +
                                "Activarla pide la contraseña maestra."
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
            ListItem(
                headlineContent = { Text("Derivación de la clave") },
                supportingContent = {
                    Column {
                        Text(kdfLabel(kdfParams))
                        if (kdfUpgradeWarning != null) {
                            Text(kdfUpgradeWarning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Frase antiphishing") },
                supportingContent = {
                    Text(
                        if (antiPhishingPhrase == null) {
                            "Sin frase. Elige una: la pantalla de desbloqueo la mostrará siempre antes de pedir " +
                                "la contraseña maestra y una app que la imite no la conocerá. Cambiarla pide la contraseña maestra."
                        } else {
                            "«$antiPhishingPhrase». Si al desbloquear no la ves, no escribas la contraseña maestra. " +
                                "Cambiarla pide la contraseña maestra."
                        },
                    )
                },
                modifier = Modifier.clickable(enabled = !viewModel.busy) {
                    reauth = Reauth(
                        title = "Frase antiphishing",
                        text = "Escribe la contraseña maestra para confirmar que eres tú.",
                        confirmLabel = "Continuar",
                        onVerified = { editingPhrase = true },
                    )
                },
            )
            HorizontalDivider()

            SectionTitle("Autorrelleno")
            ListItem(
                headlineContent = { Text("Rellenar en otras apps y en Chrome") },
                supportingContent = {
                    Text(
                        if (autofillEnabled) {
                            "Activado. Al tocar un campo de usuario o contraseña aparecerá «Contraseñora» en el teclado."
                        } else {
                            "Desactivado. Toca aquí para elegir Contraseñora como servicio de autorrelleno."
                        },
                    )
                },
                trailingContent = { Switch(checked = autofillEnabled, onCheckedChange = { openAutofillSettings() }) },
                modifier = Modifier.clickable { openAutofillSettings() },
            )
            Text(
                "En Chrome, además: Ajustes → Servicios de autocompletar → «Autocompletar con otro servicio». " +
                    "El teclado solo ve la palabra «Contraseñora»: la cuenta la eliges dentro de la app, con la huella.",
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
                    headlineContent = { Text("Comprobar mi código de recuperación") },
                    supportingContent = {
                        Text("Asegúrate de vez en cuando de que el papel sigue legible y bien copiado, mientras aún puedes hacer uno nuevo.")
                    },
                    modifier = Modifier.clickable(enabled = !viewModel.busy) { checkingRecoveryCode = true },
                )
                ListItem(
                    headlineContent = { Text("Nuevo código de recuperación") },
                    supportingContent = { Text("Si has perdido el papel donde lo apuntaste o alguien lo ha visto.") },
                    modifier = Modifier.clickable(enabled = !viewModel.busy) { onNewRecoveryCode() },
                )
            }
            HorizontalDivider()

            SectionTitle("Copias de seguridad")
            Text(
                "La copia es un archivo cifrado con tu contraseña maestra actual: su seguridad fuera del " +
                    "teléfono es la de esa contraseña. El selector intenta ocultar la nube y Contraseñora rechaza " +
                    "los servicios en la nube que conoce, pero si la guardas en Descargas y tienes activa una " +
                    "sincronización de carpetas podría subirse: pásala después a un USB o a un ordenador y " +
                    "bórrala del teléfono. Si pierdes el móvil, esa copia es la única forma de recuperar tus " +
                    "$entryCount entradas. Los códigos 2FA van dentro, cifrados: para abrirlos en otro móvil " +
                    "hará falta también tu código de recuperación.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ListItem(
                headlineContent = { Text("Última copia verificada") },
                supportingContent = { Text(lastBackupLabel(backupStatus) { formatDate(it) }) },
            )
            ListItem(
                headlineContent = { Text("Exportar copia cifrada") },
                supportingContent = { Text("Pide confirmación y tu huella o contraseña maestra; el archivo se relee y se verifica.") },
                modifier = Modifier.clickable(enabled = !viewModel.busy) { confirmExport = true },
            )
            ListItem(
                headlineContent = { Text("Restaurar copia") },
                supportingContent = { Text("Sustituye todo el contenido actual por el de la copia. Pide la contraseña maestra actual y la de la copia.") },
                modifier = Modifier.clickable(enabled = !viewModel.busy) { confirmRestore = true },
            )
            if (viewModel.canUndoRestore) {
                ListItem(
                    headlineContent = { Text("Volver a la bóveda anterior") },
                    supportingContent = {
                        Text(
                            "Pone en su sitio la bóveda que había antes de la última restauración o del último " +
                                "cambio, bloqueada y sin huella, y guarda la de ahora en su lugar. Pide la contraseña maestra.",
                        )
                    },
                    modifier = Modifier.clickable(enabled = !viewModel.busy) { undoAction = UndoAction.UNDO },
                )
                ListItem(
                    headlineContent = { Text("Descartar la bóveda anterior") },
                    supportingContent = { Text("Borra para siempre la bóveda anterior que se conserva en este teléfono. No se puede deshacer.") },
                    modifier = Modifier.clickable(enabled = !viewModel.busy) { undoAction = UndoAction.DISCARD },
                )
            }
            HorizontalDivider()

            SectionTitle("Privacidad")
            Text(
                "• Sin permiso de Internet: Android no deja que la app abra ninguna conexión.\n" +
                    "• Cifrado AES-256-GCM con clave derivada por Argon2id (parámetros en «Derivación de la clave»).\n" +
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
                changeSettings(settings.copy(autoLockSeconds = it))
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
                changeSettings(settings.copy(clipboardClearSeconds = it))
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

    // Con la bóveda abierta la sesión exige siempre la contraseña maestra actual (B-31); la huella
    // no la sustituye.
    restoreUri?.let { uri ->
        RestoreBackupDialog(
            text = "Escribe la contraseña maestra con la que se hizo la copia y la contraseña maestra " +
                "actual. Al terminar, la contraseña maestra será la de la copia.",
            onConfirm = { backupPassword, currentPassword ->
                restoreUri = null
                viewModel.restoreBackup(uri, backupPassword, currentPassword)
            },
            onDismiss = { restoreUri = null },
        )
    }

    RestoreUndoDialogs(undoAction, viewModel, onRestoreUndone) { undoAction = null }

    if (confirmExport) {
        ConfirmDialog(
            title = "¿Exportar una copia?",
            text = "El archivo contendrá todas tus entradas y códigos 2FA, cifrados solo con tu contraseña maestra " +
                "(sin la capa de hardware del teléfono). Guárdalo donde nadie más llegue.",
            confirmLabel = "Continuar",
            onConfirm = {
                confirmExport = false
                authorizeExport()
            },
            onDismiss = { confirmExport = false },
        )
    }

    reauth?.let { pending ->
        PasswordPromptDialog(
            title = pending.title,
            text = pending.text,
            confirmLabel = pending.confirmLabel,
            label = "Contraseña maestra",
            onConfirm = { password ->
                reauth = null
                viewModel.verifyMasterPassword(password, pending.onVerified)
            },
            onDismiss = { reauth = null },
        )
    }

    if (editingPhrase) {
        AntiPhishingPhraseDialog(
            current = antiPhishingPhrase,
            onConfirm = { phrase ->
                editingPhrase = false
                phrases.save(phrase)
                viewModel.message("Frase antiphishing guardada.")
            },
            onDismiss = { editingPhrase = false },
        )
    }

    if (checkingRecoveryCode) {
        RecoveryCodeCheckDialog(
            busy = viewModel.busy,
            onConfirm = { typed ->
                checkingRecoveryCode = false
                viewModel.checkOtpRecoveryCode(typed)
            },
            onDismiss = { checkingRecoveryCode = false },
        )
    }
}

/** Operación sensible que espera a que el usuario vuelva a escribir la contraseña maestra. */
private class Reauth(
    val title: String,
    val text: String,
    val confirmLabel: String,
    val onVerified: () -> Unit,
)

/** Pide la nueva frase antiphishing; solo deja confirmar una válida (M-04). */
@Composable
private fun AntiPhishingPhraseDialog(
    current: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf(current.orEmpty()) }
    val problem = antiPhishingPhraseProblem(typed)
    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Frase antiphishing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Una frase corta que solo tú conozcas. Contraseñora la mostrará siempre antes de pedirte la " +
                        "contraseña maestra, también al rellenar en otras apps: si no la ves, no escribas la contraseña.",
                )
                NoLearningTextField(
                    value = typed,
                    onValueChange = { if (it.length <= ANTI_PHISHING_MAX_LENGTH) typed = it },
                    label = "Frase ($ANTI_PHISHING_MIN_LENGTH-$ANTI_PHISHING_MAX_LENGTH caracteres)",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                if (typed.isNotEmpty() && problem != null) {
                    Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(typed) }, enabled = problem == null) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Pide el código de recuperación 2FA para comprobarlo; solo se dirá si es correcto o no. */
@Composable
private fun RecoveryCodeCheckDialog(
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comprobar el código de recuperación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Escribe el código tal y como lo apuntaste. Contraseñora solo te dirá si es el correcto.")
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Código de recuperación") },
                    placeholder = { Text("XXXXX-XXXXX-XXXXX-XXXXX") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(typed) }, enabled = !busy && typed.isNotBlank()) { Text("Comprobar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
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
    SecureAlertDialog(
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
