package io.github.jls97.boveda.ui.vault

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jls97.boveda.BovedaApplication
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.crypto.KdfParams
import io.github.jls97.boveda.core.vault.VaultSettings
import io.github.jls97.boveda.data.ANTI_PHISHING_MAX_LENGTH
import io.github.jls97.boveda.data.ANTI_PHISHING_MIN_LENGTH
import io.github.jls97.boveda.data.AjustesApariencia
import io.github.jls97.boveda.data.AntiPhishingPhrase
import io.github.jls97.boveda.data.Tema
import io.github.jls97.boveda.data.antiPhishingPhraseProblem
import io.github.jls97.boveda.data.lastBackupLabel
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.session.OtpAccess
import io.github.jls97.boveda.ui.components.Apartado
import io.github.jls97.boveda.ui.components.Aviso
import io.github.jls97.boveda.ui.components.BotonFantasma
import io.github.jls97.boveda.ui.components.BotonPrimario
import io.github.jls97.boveda.ui.components.BotonSecundario
import io.github.jls97.boveda.ui.components.CampoCodigo
import io.github.jls97.boveda.ui.components.ChoiceDialog
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.Etiqueta
import io.github.jls97.boveda.ui.components.Ficha
import io.github.jls97.boveda.ui.components.Fila
import io.github.jls97.boveda.ui.components.FormaResguardo
import io.github.jls97.boveda.ui.components.InsecureDeviceWarning
import io.github.jls97.boveda.ui.components.Interruptor
import io.github.jls97.boveda.ui.components.Isotipo
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.OpenLocalDocument
import io.github.jls97.boveda.ui.components.Pantalla
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.PasswordPromptDialog
import io.github.jls97.boveda.ui.components.RestoreBackupDialog
import io.github.jls97.boveda.ui.components.Salida
import io.github.jls97.boveda.ui.components.SecureAlertDialog
import io.github.jls97.boveda.ui.components.SelectorSegmentado
import io.github.jls97.boveda.ui.components.StrengthMeter
import io.github.jls97.boveda.ui.components.TipoAviso
import io.github.jls97.boveda.ui.components.Trabajando
import io.github.jls97.boveda.ui.components.autoLockLabel
import io.github.jls97.boveda.ui.components.desbordar
import io.github.jls97.boveda.ui.components.durationLabel
import io.github.jls97.boveda.ui.components.findActivity
import io.github.jls97.boveda.ui.components.formatDate
import io.github.jls97.boveda.ui.components.hasSecureLockScreen
import io.github.jls97.boveda.ui.components.kdfLabel
import io.github.jls97.boveda.ui.components.sombraPapel
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Personalidad
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.YoungSerif
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import io.github.jls97.boveda.ui.theme.voz
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    // También fuera de la bóveda: el desbloqueo ya habla con esta voz y en este tema.
    val apariencia = remember(context) { (context.applicationContext as BovedaApplication).apariencia }
    val ajustesApariencia by apariencia.ajustes.collectAsStateWithLifecycle()
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
            onPickExportDestination("contrasenora-$date.bvd")
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

    AjustesContenido(
        settings = settings,
        deviceSecure = deviceSecure,
        deviceKeySecurityLevel = deviceKeySecurityLevel,
        deviceKeyWarning = deviceKeyWarning,
        biometricAvailable = biometricAvailable,
        biometricEnabled = biometricEnabled,
        kdfParams = kdfParams,
        kdfUpgradeWarning = kdfUpgradeWarning,
        antiPhishingPhrase = antiPhishingPhrase,
        autofillEnabled = autofillEnabled,
        otpAccess = otpAccess,
        otpCount = otpCount,
        entryCount = entryCount,
        lastBackup = lastBackupLabel(backupStatus) { formatDate(it) },
        canUndoRestore = viewModel.canUndoRestore,
        apariencia = ajustesApariencia,
        // Comprobar la contraseña maestra tarda unos segundos: que se vea que algo está en marcha.
        busy = viewModel.busy,
        snackbar = snackbar,
        onBack = { viewModel.back() },
        onAutoLock = { choosingAutoLock = true },
        onClipboard = { choosingClipboard = true },
        onBiometricChange = { setBiometric(it) },
        onChangePassword = { changingPassword = true },
        onEditPhrase = {
            reauth = Reauth(
                title = "Frase antiphishing",
                text = "Escribe la contraseña maestra para confirmar que eres tú.",
                confirmLabel = "Continuar",
                onVerified = { editingPhrase = true },
            )
        },
        onAutofill = { openAutofillSettings() },
        onRecoverOtp = onRecoverOtp,
        onCheckRecoveryCode = { checkingRecoveryCode = true },
        onNewRecoveryCode = onNewRecoveryCode,
        onExport = { confirmExport = true },
        onRestore = { confirmRestore = true },
        onUndoRestore = { undoAction = UndoAction.UNDO },
        onDiscardUndo = { undoAction = UndoAction.DISCARD },
        onPersonalidad = { apariencia.cambiarPersonalidad(it) },
        onTema = { apariencia.cambiarTema(it) },
        onLock = { viewModel.lock() },
    )

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
            peligro = true,
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

/**
 * Ajustes sin estado: la libreta de la Contraseñora, en seis apartados numerados (seguridad,
 * autorrelleno, códigos 2FA, copias, apariencia y privacidad), el botón de bloquear y el pie con el
 * logotipo. Todo lo que se ve llega como dato y cada toque sale como callback; los diálogos, las
 * comprobaciones y la contraseña maestra viven en [SettingsScreen].
 */
@Composable
internal fun AjustesContenido(
    settings: VaultSettings,
    deviceSecure: Boolean,
    deviceKeySecurityLevel: String,
    deviceKeyWarning: String?,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    kdfParams: KdfParams,
    kdfUpgradeWarning: String?,
    antiPhishingPhrase: String?,
    autofillEnabled: Boolean,
    otpAccess: OtpAccess,
    otpCount: Int,
    entryCount: Int,
    lastBackup: String,
    canUndoRestore: Boolean,
    apariencia: AjustesApariencia,
    busy: Boolean,
    onBack: () -> Unit,
    onAutoLock: () -> Unit,
    onClipboard: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onChangePassword: () -> Unit,
    onEditPhrase: () -> Unit,
    onAutofill: () -> Unit,
    onRecoverOtp: () -> Unit,
    onCheckRecoveryCode: () -> Unit,
    onNewRecoveryCode: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onUndoRestore: () -> Unit,
    onDiscardUndo: () -> Unit,
    onPersonalidad: (Personalidad) -> Unit,
    onTema: (Tema) -> Unit,
    onLock: () -> Unit,
    snackbar: SnackbarHostState? = null,
    scroll: ScrollState = rememberScrollState(),
) {
    Pantalla(
        titulo = "Ajustes",
        antetitulo = voz("La libreta de la Contraseñora", "Contraseñora"),
        salida = Salida(onBack),
        snackbar = snackbar,
        ocupado = busy,
        scroll = scroll,
    ) {
        Seguridad(
            settings = settings,
            deviceSecure = deviceSecure,
            deviceKeySecurityLevel = deviceKeySecurityLevel,
            deviceKeyWarning = deviceKeyWarning,
            biometricAvailable = biometricAvailable,
            biometricEnabled = biometricEnabled,
            kdfParams = kdfParams,
            kdfUpgradeWarning = kdfUpgradeWarning,
            antiPhishingPhrase = antiPhishingPhrase,
            busy = busy,
            onAutoLock = onAutoLock,
            onClipboard = onClipboard,
            onBiometricChange = onBiometricChange,
            onChangePassword = onChangePassword,
            onEditPhrase = onEditPhrase,
        )
        Autorrelleno(autofillEnabled, onAutofill)
        Codigos2fa(otpAccess, otpCount, busy, onRecoverOtp, onCheckRecoveryCode, onNewRecoveryCode)
        Copias(entryCount, lastBackup, canUndoRestore, busy, onExport, onRestore, onUndoRestore, onDiscardUndo)
        AparienciaApartado(apariencia, onPersonalidad, onTema)
        Privacidad()
        BotonSecundario(
            "Bloquear ahora",
            onLock,
            Modifier.fillMaxWidth().padding(top = Spacing.s8),
            icono = R.drawable.ic_candado,
        )
        Pie()
    }
}

// region Apartados

@Composable
private fun Seguridad(
    settings: VaultSettings,
    deviceSecure: Boolean,
    deviceKeySecurityLevel: String,
    deviceKeyWarning: String?,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    kdfParams: KdfParams,
    kdfUpgradeWarning: String?,
    antiPhishingPhrase: String?,
    busy: Boolean,
    onAutoLock: () -> Unit,
    onClipboard: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onChangePassword: () -> Unit,
    onEditPhrase: () -> Unit,
) {
    Apartado("Seguridad", numero = "I", descripcion = "Lo que protege la bóveda en este teléfono.")
    Aparece(!deviceSecure) { InsecureDeviceWarning(Modifier.padding(top = Spacing.s2)) }
    // M-07: la alternancia StrongBox → TEE era silenciosa; ahora se ve qué protege la clave.
    PlacaClave(deviceKeySecurityLevel, Modifier.padding(top = Spacing.s3))
    Aparece(deviceKeyWarning != null) {
        Aviso(
            TipoAviso.Aviso,
            "Sin protección de hardware",
            mensaje = deviceKeyWarning,
            modifier = Modifier.padding(top = Spacing.s3),
        )
    }
    Renglones(Modifier.padding(top = Spacing.s3)) {
        Fila(
            "Bloqueo automático",
            valor = autoLockLabel(settings.autoLockSeconds),
            descripcion = "Siempre al apagar la pantalla.",
            enabled = !busy,
            onClick = onAutoLock,
        )
        Renglon()
        Fila(
            "Borrar portapapeles",
            valor = "A los ${durationLabel(settings.clipboardClearSeconds)} de copiar",
            enabled = !busy,
            onClick = onClipboard,
        )
        Renglon()
        FilaInterruptor(
            "Desbloqueo con huella",
            descripcion = if (biometricAvailable) {
                "Abrirá la bóveda cualquier huella ya registrada en este teléfono (solo huellas fuertes). Revisa " +
                    "las huellas en los ajustes del sistema antes de activarla. La clave se destruye al inscribir " +
                    "una huella nueva o quitar el bloqueo de pantalla. Activarla pide la contraseña maestra."
            } else {
                "No hay ninguna huella segura registrada en el teléfono."
            },
            activado = biometricEnabled,
            onCambio = onBiometricChange,
            enabled = !busy && (biometricAvailable || biometricEnabled),
            // Sin huella registrada la descripción dice por qué no se puede tocar: se queda legible.
            atenuarDescripcion = busy,
        )
        Renglon()
        Fila("Cambiar contraseña maestra", enabled = !busy, onClick = onChangePassword)
        Renglon()
        Fila("Derivación de la clave", valor = kdfLabel(kdfParams))
        Aparece(kdfUpgradeWarning != null) {
            Aviso(
                TipoAviso.Aviso,
                "Derivación sin actualizar",
                mensaje = kdfUpgradeWarning,
                modifier = Modifier.padding(bottom = Spacing.s3),
            )
        }
        Renglon()
        Fila(
            "Frase antiphishing",
            valor = antiPhishingPhrase?.let { "«$it»" } ?: "Sin frase",
            descripcion = if (antiPhishingPhrase == null) {
                "Elige una: la pantalla de desbloqueo la mostrará siempre antes de pedir la contraseña maestra y " +
                    "una app que la imite no la conocerá. Elegirla pide la contraseña maestra."
            } else {
                "Si al desbloquear no la ves, no escribas la contraseña maestra. Cambiarla pide la contraseña maestra."
            },
            enabled = !busy,
            onClick = onEditPhrase,
        )
    }
}

@Composable
private fun Autorrelleno(autofillEnabled: Boolean, onAutofill: () -> Unit) {
    Apartado("Autorrelleno", numero = "II")
    FilaInterruptor(
        "Rellenar en otras apps y en Chrome",
        descripcion = if (autofillEnabled) {
            "Activado. Al tocar un campo de usuario o contraseña aparecerá «Contraseñora» en el teclado."
        } else {
            "Desactivado. Toca aquí para elegir Contraseñora como servicio de autorrelleno."
        },
        activado = autofillEnabled,
        onCambio = { onAutofill() },
    )
    Aviso(
        TipoAviso.Info,
        "En Chrome, un paso más",
        mensaje = "Ajustes → Servicios de autocompletar → «Autocompletar con otro servicio». El teclado solo ve " +
            "la palabra «Contraseñora»: la cuenta la eliges dentro de la app, con la huella.",
        modifier = Modifier.padding(top = Spacing.s2),
    )
}

@Composable
private fun Codigos2fa(
    otpAccess: OtpAccess,
    otpCount: Int,
    busy: Boolean,
    onRecoverOtp: () -> Unit,
    onCheckRecoveryCode: () -> Unit,
    onNewRecoveryCode: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    Apartado("Códigos 2FA", numero = "III")
    AnimatedContent(
        targetState = otpAccess,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(Motion.BASE, delayMillis = Motion.FAST)) togetherWith fadeOut(tween(Motion.FAST))
            }
        },
        label = "estado de los códigos 2FA",
    ) { acceso ->
        Column {
            when (acceso) {
                OtpAccess.LOCKED -> {
                    Aviso(
                        TipoAviso.Aviso,
                        "Bloqueados en este móvil",
                        mensaje = "Este móvil no tiene la llave de huella que los abre (copia restaurada o huellas " +
                            "cambiadas). Recupéralos con tu código de recuperación.",
                        modifier = Modifier.padding(top = Spacing.s2, bottom = Spacing.s1),
                    )
                    Fila(
                        "Recuperar con el código de recuperación",
                        enabled = !busy,
                        onClick = onRecoverOtp,
                    )
                }
                OtpAccess.NONE -> Fila(
                    "Todavía no hay ninguno",
                    descripcion = "Se añaden desde cada entrada. Cada código se abrirá solo con tu huella.",
                    final = { Etiqueta("2FA", icono = R.drawable.ic_reloj) },
                )
                OtpAccess.READY -> {
                    Fila(
                        if (otpCount == 1) "1 código" else "$otpCount códigos",
                        descripcion = "Cada uno se abre solo con tu huella, aunque la bóveda esté desbloqueada.",
                        final = { Etiqueta("2FA", icono = R.drawable.ic_reloj) },
                    )
                    Renglon()
                    Fila(
                        "Comprobar mi código de recuperación",
                        descripcion = "Asegúrate de vez en cuando de que el papel sigue legible y bien copiado, " +
                            "mientras aún puedes hacer uno nuevo.",
                        enabled = !busy,
                        onClick = onCheckRecoveryCode,
                    )
                    Renglon()
                    Fila(
                        "Nuevo código de recuperación",
                        descripcion = "Si has perdido el papel donde lo apuntaste o alguien lo ha visto.",
                        enabled = !busy,
                        onClick = onNewRecoveryCode,
                    )
                }
            }
        }
    }
}

@Composable
private fun Copias(
    entryCount: Int,
    lastBackup: String,
    canUndoRestore: Boolean,
    busy: Boolean,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onUndoRestore: () -> Unit,
    onDiscardUndo: () -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Apartado(
        "Copias de seguridad",
        numero = "IV",
        descripcion = "Si pierdes el móvil, una copia es la única forma de recuperar tus $entryCount entradas. Es un " +
            "archivo cifrado con tu contraseña maestra actual: su seguridad fuera del teléfono es la de esa " +
            "contraseña. Los códigos 2FA van dentro, cifrados: para abrirlos en otro móvil hará falta también tu " +
            "código de recuperación.",
    )
    Resguardo(
        Modifier.padding(top = Spacing.s3),
        arriba = {
            val (cuando, desdeEntonces) = partesDeLaCopia(lastBackup)
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Column(Modifier.weight(1f)) {
                    Text("Última copia verificada", style = t.label, color = c.textSecondary)
                    Text(
                        cuando,
                        style = serifMediana,
                        color = c.textPrimary,
                        modifier = Modifier.padding(top = Spacing.s0_5),
                    )
                    if (desdeEntonces != null) {
                        Text(desdeEntonces, style = t.small, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s1))
                    }
                }
                Icon(
                    painterResource(R.drawable.ic_copia),
                    contentDescription = null,
                    tint = c.textLink,
                    modifier = Modifier.size(Sizes.iconLg),
                )
            }
        },
        matriz = {
            Text(
                "Pide confirmación y tu huella o contraseña maestra; el archivo se relee y se verifica.",
                style = t.small,
                color = c.textSecondary,
            )
            BotonPrimario(
                "Exportar copia cifrada",
                onExport,
                Modifier.fillMaxWidth().padding(top = Spacing.s3),
                enabled = !busy,
                icono = R.drawable.ic_copia,
            )
        },
    )
    Aviso(
        TipoAviso.Aviso,
        "Guárdala fuera del teléfono",
        mensaje = "El selector intenta ocultar la nube y Contraseñora rechaza los servicios en la nube que conoce, " +
            "pero si la guardas en Descargas y tienes activa una sincronización de carpetas podría subirse: pásala " +
            "después a un USB o a un ordenador y bórrala del teléfono.",
        modifier = Modifier.padding(top = Spacing.s3),
    )
    Renglones(Modifier.padding(top = Spacing.s2)) {
        Fila(
            "Restaurar copia",
            descripcion = "Sustituye todo el contenido actual por el de la copia. Pide la contraseña maestra actual " +
                "y la de la copia.",
            enabled = !busy,
            onClick = onRestore,
        )
        Aparece(canUndoRestore) {
            Column {
                Renglon()
                Fila(
                    "Volver a la bóveda anterior",
                    descripcion = "Pone en su sitio la bóveda que había antes de la última restauración o del último " +
                        "cambio, bloqueada y sin huella, y guarda la de ahora en su lugar. Pide la contraseña maestra.",
                    enabled = !busy,
                    onClick = onUndoRestore,
                )
                Renglon()
                Fila(
                    "Descartar la bóveda anterior",
                    descripcion = "Borra para siempre la bóveda anterior que se conserva en este teléfono. No se puede " +
                        "deshacer.",
                    enabled = !busy,
                    peligro = true,
                    onClick = onDiscardUndo,
                )
            }
        }
    }
}

@Composable
private fun AparienciaApartado(
    apariencia: AjustesApariencia,
    onPersonalidad: (Personalidad) -> Unit,
    onTema: (Tema) -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Apartado("Apariencia", numero = "V")
    Text("Personalidad", style = t.bodyStrong, color = c.textPrimary, modifier = Modifier.padding(top = Spacing.s2))
    Text(
        "El tono de los textos. Los avisos importantes dicen lo mismo en los dos.",
        style = t.small,
        color = c.textSecondary,
    )
    // Lado a lado caben con la letra normal; a partir de 1,3× cada una va en su renglón.
    val apiladas = LocalDensity.current.fontScale > 1.3f
    val contrasenora: @Composable (Modifier) -> Unit = { modificador ->
        FichaPersonalidad(
            titulo = "Contraseñora",
            lema = "Con su humor de siempre.",
            simple = false,
            elegida = apariencia.personalidad == Personalidad.Contrasenora,
            onElegir = { onPersonalidad(Personalidad.Contrasenora) },
            modifier = modificador,
        )
    }
    val sobria: @Composable (Modifier) -> Unit = { modificador ->
        FichaPersonalidad(
            titulo = "Sobria",
            lema = "Mismos avisos, sin chistes.",
            simple = true,
            elegida = apariencia.personalidad == Personalidad.Sobria,
            onElegir = { onPersonalidad(Personalidad.Sobria) },
            modifier = modificador,
        )
    }
    if (apiladas) {
        Column(
            Modifier.fillMaxWidth().padding(top = Spacing.s3).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            contrasenora(Modifier.fillMaxWidth())
            sobria(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = Spacing.s3)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            contrasenora(Modifier.weight(1f).fillMaxHeight())
            sobria(Modifier.weight(1f).fillMaxHeight())
        }
    }
    Text("Tema", style = t.bodyStrong, color = c.textPrimary, modifier = Modifier.padding(top = Spacing.s6))
    Text(
        "«Sistema» sigue el modo oscuro del teléfono.",
        style = t.small,
        color = c.textSecondary,
        modifier = Modifier.padding(bottom = Spacing.s3),
    )
    SelectorSegmentado(
        opciones = listOf(Tema.Sistema to "Sistema", Tema.Claro to "Claro", Tema.Oscuro to "Oscuro"),
        seleccionada = apariencia.tema,
        onSeleccion = onTema,
    )
}

@Composable
private fun Privacidad() {
    Apartado("Privacidad", numero = "VI")
    Renglones(Modifier.padding(top = Spacing.s1)) {
        Garantia("Sin permiso de Internet: Android no deja que la app abra ninguna conexión.")
        Renglon()
        Garantia("Cifrado AES-256-GCM con clave derivada por Argon2id (parámetros en «Derivación de la clave»).")
        Renglon()
        Garantia("Capa extra ligada al chip de seguridad del teléfono (Android Keystore).")
        Renglon()
        Garantia("Sin capturas de pantalla, sin copias en la nube y sin autorrelleno de terceros.")
    }
    Aviso(
        TipoAviso.Info,
        voz("Un consejo de la casa", "Consejo"),
        mensaje = "Desactiva la sincronización del portapapeles del teclado y de HyperOS.",
        modifier = Modifier.padding(top = Spacing.s3),
    )
}

/** El colofón de la libreta: el logotipo y la firma de la casa. */
@Composable
private fun Pie() {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.s10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineaPunteada()
        Image(
            painter = logotipoDelTema(),
            contentDescription = "Contraseñora",
            modifier = Modifier.padding(top = Spacing.s8).width(160.dp),
        )
        Text(
            voz("Tus claves, en buenas manos.", "Gestor de contraseñas y códigos 2FA."),
            style = t.small,
            color = c.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s3),
        )
    }
}

// endregion

// region Piezas de la libreta

/**
 * Placa de la clave de este teléfono: dónde vive (StrongBox, TEE, Software…) y para qué sirve, como
 * la chapa atornillada a una caja fuerte.
 */
@Composable
private fun PlacaClave(nivel: String, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Ficha(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.bgSunken),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_escudo), contentDescription = null, tint = c.textLink, modifier = Modifier.size(Sizes.iconLg))
            }
            Column(Modifier.weight(1f)) {
                Text("Clave de este teléfono", style = t.label, color = c.textSecondary)
                Text(
                    "Protegida por $nivel",
                    style = serifMediana,
                    color = c.textPrimary,
                    modifier = Modifier.padding(top = Spacing.s0_5),
                )
            }
        }
        LineaPunteada(Modifier.padding(vertical = Spacing.s3))
        Text(
            "Es la capa que impide abrir una copia de los archivos de la app fuera de este teléfono.",
            style = t.small,
            color = c.textSecondary,
        )
    }
}

/**
 * Fila de libreta con un [Interruptor] al final que se pulsa entera: TalkBack la lee como un
 * interruptor con su título y su descripción. Mismas medidas que [Fila].
 */
@Composable
private fun FilaInterruptor(
    titulo: String,
    descripcion: String?,
    activado: Boolean,
    onCambio: (Boolean) -> Unit,
    enabled: Boolean = true,
    atenuarDescripcion: Boolean = !enabled,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .desbordar(Spacing.s2)
            .clip(ContrasenoraShapes.sm)
            .toggleable(value = activado, enabled = enabled, role = Role.Switch, onValueChange = onCambio)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.s2, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = t.bodyStrong, color = if (enabled) c.textPrimary else c.textDisabled)
            if (descripcion != null) {
                Text(descripcion, style = t.small, color = if (atenuarDescripcion) c.textDisabled else c.textSecondary)
            }
        }
        Interruptor(activado, onCambio = null, enabled = enabled)
    }
}

/**
 * Personalidad como una ficha que se elige: el isotipo (sin gafas en Sobria), el nombre y su lema.
 * La elegida lleva borde ciruela de 2 dp y una marca de conforme; TalkBack la lee como botón de opción.
 */
@Composable
private fun FichaPersonalidad(
    titulo: String,
    lema: String,
    simple: Boolean,
    elegida: Boolean,
    onElegir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    val borde by animateColorAsState(
        targetValue = if (elegida) c.brandPrimary else c.borderSubtle,
        animationSpec = if (reduced) snap() else tween(Motion.BASE, easing = Motion.Standard),
        label = "borde de la personalidad",
    )
    val grosor by animateDpAsState(
        targetValue = if (elegida) 2.dp else 1.dp,
        animationSpec = if (reduced) snap() else tween(Motion.BASE, easing = Motion.Standard),
        label = "grosor del borde",
    )
    val forma = ContrasenoraShapes.md
    Surface(
        modifier = modifier
            .sombraPapel(forma, c.isDark)
            .clip(forma)
            .selectable(selected = elegida, role = Role.RadioButton, onClick = onElegir),
        shape = forma,
        color = c.bgSurface,
        contentColor = c.textPrimary,
        border = BorderStroke(grosor, borde),
    ) {
        Box {
            Column(Modifier.padding(Spacing.s4)) {
                Isotipo(Modifier.size(44.dp), simple = simple)
                Text(
                    titulo,
                    style = serifMediana,
                    color = c.textPrimary,
                    modifier = Modifier.padding(top = Spacing.s3),
                )
                Text(lema, style = t.small, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s0_5))
            }
            AnimatedVisibility(
                visible = elegida,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.s3),
                enter = if (reduced) EnterTransition.None else fadeIn(tween(Motion.FAST)) + scaleIn(tween(Motion.BASE, easing = Motion.Emphasized), initialScale = 0.4f),
                exit = if (reduced) ExitTransition.None else fadeOut(tween(Motion.FAST)) + scaleOut(tween(Motion.FAST), targetScale = 0.6f),
            ) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(c.brandPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.brandOnPrimary, modifier = Modifier.size(Sizes.iconSm))
                }
            }
        }
    }
}

/** Una garantía de la casa, con su marca de conforme delante. */
@Composable
private fun Garantia(texto: String) {
    val c = ContrasenoraTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s3),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = c.textLink,
            modifier = Modifier.padding(top = 2.dp).size(Sizes.iconMd),
        )
        Text(texto, style = ContrasenoraTheme.type.body, color = c.textPrimary)
    }
}

/** Young Serif a 20 sp: lo primero que se lee en la placa, el resguardo y las fichas de personalidad. */
private val serifMediana: TextStyle
    @Composable @ReadOnlyComposable
    get() = ContrasenoraTheme.type.title2.copy(fontFamily = YoungSerif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp)

/** Renglones de una libreta: filas seguidas, separadas con [Renglon]. */
@Composable
private fun Renglones(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth(), content = content)

/** La línea de puntos entre dos renglones. */
@Composable
private fun Renglon() = LineaPunteada(color = ContrasenoraTheme.colors.borderSubtle)

/**
 * Resguardo de ventanilla: papel con dos muescas y una línea perforada que separa el resguardo de su
 * matriz. Las muescas se colocan donde empieza la matriz, mida lo que mida (fuente al 200 %).
 */
@Composable
private fun Resguardo(
    modifier: Modifier = Modifier,
    arriba: @Composable ColumnScope.() -> Unit,
    matriz: @Composable ColumnScope.() -> Unit,
) {
    val c = ContrasenoraTheme.colors
    // Distancia en píxeles desde abajo hasta la perforación: se mide al colocar y la forma la lee al
    // dibujar el papel y su sombra, en el mismo fotograma.
    val corte = remember { floatArrayOf(0f) }
    val forma = remember(corte) { FormaResguardoMedida(corte) }
    Layout(
        content = {
            Column(Modifier.fillMaxWidth().padding(Spacing.s4), content = arriba)
            LineaPunteada(Modifier.padding(horizontal = Spacing.s4), color = c.borderDefault)
            Column(Modifier.fillMaxWidth().padding(Spacing.s4), content = matriz)
        },
        modifier = modifier
            .fillMaxWidth()
            .sombraPapel(forma, c.isDark)
            .drawBehind {
                val contorno = forma.createOutline(size, layoutDirection, this)
                drawOutline(contorno, c.bgSurface)
                drawOutline(contorno, c.borderSubtle, style = Stroke(1.dp.toPx()))
            },
    ) { medibles, restricciones ->
        val libres = restricciones.copy(minHeight = 0)
        val (resguardo, perforacion, talon) = medibles.map { it.measure(libres) }
        val alto = resguardo.height + perforacion.height + talon.height
        corte[0] = talon.height + perforacion.height / 2f
        layout(restricciones.maxWidth, alto) {
            resguardo.place(0, 0)
            perforacion.place(0, resguardo.height)
            talon.place(0, resguardo.height + perforacion.height)
        }
    }
}

/** [FormaResguardo] con las muescas a la altura que [Resguardo] mide para su matriz. */
private class FormaResguardoMedida(private val corte: FloatArray) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        FormaResguardo(with(density) { corte[0].toDp() }).createOutline(size, layoutDirection, density)
}

/**
 * «Última copia: 3 oct 2026, 21:40. 2 cambios sin copiar desde entonces.» → «3 oct 2026, 21:40» y lo
 * demás, para el resguardo, que ya dice «Última copia verificada». Se corta por el último punto: la
 * fecha puede llevar los suyos («3 oct. 2026» en otros idiomas). Si el texto no empieza así, entero.
 */
private fun partesDeLaCopia(texto: String): Pair<String, String?> {
    val sinPrefijo = texto.removePrefix("Última copia: ").replaceFirstChar { it.uppercase() }
    val corte = sinPrefijo.lastIndexOf(". ")
    return if (corte < 0) {
        sinPrefijo.removeSuffix(".") to null
    } else {
        sinPrefijo.substring(0, corte) to sinPrefijo.substring(corte + 2)
    }
}

/** Lo que aparece (avisos, filas de deshacer) se despliega y funde; sin animación si se han quitado. */
@Composable
private fun Aparece(visible: Boolean, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) {
            EnterTransition.None
        } else {
            expandVertically(tween(Motion.BASE, easing = Motion.Standard)) + fadeIn(tween(Motion.BASE))
        },
        exit = if (reduced) {
            ExitTransition.None
        } else {
            shrinkVertically(tween(Motion.BASE, easing = Motion.Exit)) + fadeOut(tween(Motion.FAST))
        },
    ) { content() }
}

/**
 * El logotipo en la variante del tema de la app. Con el tema forzado en Ajustes, el del sistema
 * puede no coincidir y `drawable-night` daría el del otro: entonces se carga con la configuración
 * del tema elegido.
 */
@Composable
private fun logotipoDelTema(): Painter {
    val oscuro = ContrasenoraTheme.colors.isDark
    if (oscuro == isSystemInDarkTheme()) return painterResource(R.drawable.logotipo_horizontal)
    val context = LocalContext.current
    val configuracion = LocalConfiguration.current
    val vector = remember(context, configuracion, oscuro) {
        val delTema = Configuration(configuracion).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (oscuro) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        ImageVector.vectorResource(null, context.createConfigurationContext(delTema).resources, R.drawable.logotipo_horizontal)
    }
    return rememberVectorPainter(vector)
}

// endregion

// region Diálogos

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
        text = { FraseAntiphishingCampos(typed, onTypedChange = { if (it.length <= ANTI_PHISHING_MAX_LENGTH) typed = it }) },
        confirmButton = { BotonFantasma("Guardar", { onConfirm(typed) }, enabled = problem == null) },
        dismissButton = { BotonFantasma("Cancelar", onDismiss) },
    )
}

/** El cuerpo del diálogo de la frase antiphishing: qué es y el campo, con su problema como error. */
@Composable
internal fun FraseAntiphishingCampos(typed: String, onTypedChange: (String) -> Unit) {
    val problem = antiPhishingPhraseProblem(typed)
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Text(
            "Una frase corta que solo tú conozcas. Contraseñora la mostrará siempre antes de pedirte la " +
                "contraseña maestra, también al rellenar en otras apps: si no la ves, no escribas la contraseña.",
            style = ContrasenoraTheme.type.body,
            color = ContrasenoraTheme.colors.textSecondary,
        )
        NoLearningTextField(
            value = typed,
            onValueChange = onTypedChange,
            label = "Frase antiphishing",
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            error = if (typed.isNotEmpty()) problem else null,
            ayuda = "De $ANTI_PHISHING_MIN_LENGTH a $ANTI_PHISHING_MAX_LENGTH caracteres.",
        )
    }
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                Text(
                    "Escribe el código tal y como lo apuntaste. Contraseñora solo te dirá si es el correcto.",
                    style = ContrasenoraTheme.type.body,
                    color = ContrasenoraTheme.colors.textSecondary,
                )
                CampoCodigo(
                    value = typed,
                    onValueChange = { typed = it },
                    label = "Código de recuperación",
                    placeholder = "XXXXX-XXXXX-XXXXX-XXXXX",
                    enabled = !busy,
                )
            }
        },
        confirmButton = { BotonFantasma("Comprobar", { onConfirm(typed) }, enabled = !busy && typed.isNotBlank()) },
        dismissButton = { BotonFantasma("Cancelar", onDismiss) },
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
            CambioContrasenaCampos(
                current = current,
                onCurrentChange = { current = it },
                newPassword = newPassword,
                onNewPasswordChange = { newPassword = it },
                confirmation = confirmation,
                onConfirmationChange = { confirmation = it },
                busy = busy,
            )
        },
        confirmButton = {
            BotonFantasma(
                "Cambiar",
                { onConfirm(current, newPassword, confirmation) },
                enabled = !busy && current.isNotEmpty() && newPassword.isNotEmpty() && confirmation.isNotEmpty(),
            )
        },
        dismissButton = { BotonFantasma("Cancelar", onDismiss, enabled = !busy) },
    )
}

/** El cuerpo del diálogo de cambiar la contraseña maestra: la actual, la nueva con su medidor y la repetición. */
@Composable
internal fun CambioContrasenaCampos(
    current: String,
    onCurrentChange: (String) -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmation: String,
    onConfirmationChange: (String) -> Unit,
    busy: Boolean,
) {
    // Como en el alta: se avisa en cuanto la repetición deja de ser el principio de la nueva.
    val noCoincide = confirmation.isNotEmpty() && !newPassword.startsWith(confirmation)
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        PasswordField(value = current, onValueChange = onCurrentChange, label = "Contraseña actual", enabled = !busy)
        PasswordField(value = newPassword, onValueChange = onNewPasswordChange, label = "Nueva contraseña", enabled = !busy)
        StrengthMeter(newPassword)
        PasswordField(
            value = confirmation,
            onValueChange = onConfirmationChange,
            label = "Repite la nueva",
            imeAction = ImeAction.Done,
            enabled = !busy,
            error = if (noCoincide) "No coincide con la nueva." else null,
        )
        if (busy) Trabajando("Cifrando…")
    }
}

// endregion
