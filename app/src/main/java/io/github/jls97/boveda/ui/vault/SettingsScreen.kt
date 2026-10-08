package io.github.jls97.boveda.ui.vault

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
import io.github.jls97.boveda.security.KeySecurityLevel
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
import io.github.jls97.boveda.ui.components.InsecureDeviceWarning
import io.github.jls97.boveda.ui.components.Interruptor
import io.github.jls97.boveda.ui.components.Isotipo
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.Mostrador
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.OpenLocalDocument
import io.github.jls97.boveda.ui.components.Pantalla
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.PasswordPromptDialog
import io.github.jls97.boveda.ui.components.Redondel
import io.github.jls97.boveda.ui.components.Resguardo
import io.github.jls97.boveda.ui.components.RestoreBackupDialog
import io.github.jls97.boveda.ui.components.Salida
import io.github.jls97.boveda.ui.components.SecureAlertDialog
import io.github.jls97.boveda.ui.components.SelectorSegmentado
import io.github.jls97.boveda.ui.components.Sello
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
import io.github.jls97.boveda.ui.components.tintaDe
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
        // Al día: hay copia verificada y nada la ha dejado atrás (cambios ni un aviso pendiente).
        copiaAlDia = backupStatus.lastBackupAt != 0L && backupStatus.changesSince == 0 && backupStatus.pendingReason == null,
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
    /** La última copia verificada sigue al día (true), se ha quedado atrás o no hay (false); null, sin sello. */
    copiaAlDia: Boolean? = null,
    snackbar: SnackbarHostState? = null,
    scroll: ScrollState = rememberScrollState(),
) {
    Pantalla(
        titulo = "Ajustes",
        antetitulo = voz("La libreta de la Contraseñora", "Contraseñora"),
        entradilla = resumenDeAjustes(entryCount, otpCount, settings.autoLockSeconds),
        salida = Salida(onBack),
        snackbar = snackbar,
        ocupado = busy,
        // Comprobar la contraseña maestra tarda unos segundos: se dice abajo, a la vista desde cualquier apartado.
        mostrador = { Aparece(busy) { Mostrador { Trabajando("Un momento…") } } },
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
        Copias(entryCount, lastBackup, copiaAlDia, canUndoRestore, busy, onExport, onRestore, onUndoRestore, onDiscardUndo)
        AparienciaApartado(apariencia, onPersonalidad, onTema)
        Privacidad(deviceSecure, deviceKeySecurityLevel, deviceKeyWarning, kdfUpgradeWarning)
        BotonSecundario(
            "Bloquear ahora",
            onLock,
            Modifier.fillMaxWidth().padding(top = Spacing.s8),
            icono = R.drawable.ic_candado,
        )
        Pie()
    }
}

/** «12 entradas · 4 con 2FA · se bloquea tras 1 minuto»: lo esencial de la libreta, como el recuento del fichero. */
private fun resumenDeAjustes(entryCount: Int, otpCount: Int, autoLockSeconds: Int): String {
    val entradas = if (entryCount == 1) "1 entrada" else "$entryCount entradas"
    val codigos = if (otpCount > 0) " · $otpCount con 2FA" else ""
    val bloqueo = if (autoLockSeconds == 0) "se bloquea al salir" else "se bloquea tras ${autoLockLabel(autoLockSeconds)}"
    return "$entradas$codigos · $bloqueo"
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
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Apartado("Seguridad", numero = "I", descripcion = "Lo que protege la bóveda en este teléfono.")
    Aparece(!deviceSecure) { InsecureDeviceWarning(Modifier.padding(top = Spacing.s2)) }
    // M-07: la alternancia StrongBox → TEE era silenciosa; ahora se ve qué protege la clave. Si es solo
    // de software, la propia placa lo dice con su sello.
    PlacaClave(deviceKeySecurityLevel, deviceKeyWarning, Modifier.padding(top = Spacing.s3))
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
            valor = "${durationLabel(settings.clipboardClearSeconds).replaceFirstChar { it.uppercase() }} después de copiar",
            enabled = !busy,
            onClick = onClipboard,
        )
        Renglon()
        FilaInterruptor(
            "Desbloqueo con huella",
            descripcion = if (biometricAvailable) {
                "Activarla pide la contraseña maestra."
            } else {
                "No hay ninguna huella segura registrada en el teléfono."
            },
            activado = biometricEnabled,
            onCambio = onBiometricChange,
            enabled = !busy && (biometricAvailable || biometricEnabled),
            // Sin huella registrada la descripción dice por qué no se puede tocar: se queda legible.
            atenuarDescripcion = busy,
        )
        if (biometricAvailable) {
            // El aviso entero, a todo el ancho: el interruptor se queda junto a su título.
            Text(
                "Abrirá la bóveda cualquier huella ya registrada en este teléfono (solo huellas fuertes). Revisa las " +
                    "huellas en los ajustes del sistema antes de activarla. La clave se destruye al inscribir una " +
                    "huella nueva o quitar el bloqueo de pantalla.",
                style = t.small,
                color = if (busy) c.textDisabled else c.textSecondary,
                modifier = Modifier.padding(bottom = Spacing.s3),
            )
        }
        Renglon()
        Fila("Cambiar contraseña maestra", enabled = !busy, onClick = onChangePassword)
        Renglon()
        Fila("Derivación de la clave", valor = kdfLabel(kdfParams))
        Aparece(kdfUpgradeWarning != null) {
            // 🔒 Visible y en color de error, como en el original (I-09): sello «Urgente» y borde de peligro.
            Aviso(
                TipoAviso.Peligro,
                "Derivación sin actualizar",
                mensaje = kdfUpgradeWarning,
                modifier = Modifier.padding(bottom = Spacing.s3),
            )
        }
        Renglon()
        FilaFrase(antiPhishingPhrase, enabled = !busy, onClick = onEditPhrase)
    }
}

@Composable
private fun Autorrelleno(autofillEnabled: Boolean, onAutofill: () -> Unit) {
    Apartado("Autorrelleno", numero = "II")
    // No se conmuta aquí: lleva a los ajustes del sistema (o dice que ya está), así que es una fila con su flecha.
    Fila(
        "Rellenar en otras apps y en Chrome",
        valor = if (autofillEnabled) "Activado" else "Desactivado",
        descripcion = if (autofillEnabled) {
            "Al tocar un campo de usuario o contraseña aparecerá «Contraseñora» en el teclado."
        } else {
            "Toca aquí para elegir Contraseñora como servicio de autorrelleno."
        },
        onClick = onAutofill,
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
                OtpAccess.LOCKED -> Aviso(
                    TipoAviso.Aviso,
                    "Bloqueados en este teléfono",
                    mensaje = "Este teléfono no tiene la llave de huella que los abre (copia restaurada o huellas " +
                        "cambiadas). Recupéralos con tu código de recuperación.",
                    accion = "Recuperar con el código",
                    onAccion = onRecoverOtp,
                    accionesActivas = !busy,
                    modifier = Modifier.padding(top = Spacing.s2),
                )
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
    copiaAlDia: Boolean?,
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
        "Copias",
        numero = "IV",
        descripcion = "Si pierdes el teléfono, una copia es la única forma de recuperar tus entradas.",
    )
    // Antes del botón de exportar, como en el original: dónde no debe quedarse la copia.
    Aviso(
        TipoAviso.Aviso,
        "Guárdala fuera del teléfono",
        mensaje = "Pásala a un USB o a un ordenador y bórrala del teléfono: el selector intenta ocultar la nube y " +
            "Contraseñora rechaza los servicios en la nube que conoce, pero en Descargas una sincronización de " +
            "carpetas podría subirla.",
        modifier = Modifier.padding(top = Spacing.s2),
    )
    Resguardo(
        Modifier.padding(top = Spacing.s3),
        arriba = {
            val copia = partesDeLaCopia(lastBackup)
            // El estado de la copia, en su sello: al día o pendiente.
            val sello: (@Composable () -> Unit)? = copiaAlDia?.let { alDia ->
                @Composable {
                    Sello(
                        selloDeLaCopia(alDia),
                        tintaDe(if (alDia) TipoAviso.Exito else TipoAviso.Aviso),
                        Modifier.clearAndSetSemantics { },
                        girado = 6f,
                    )
                }
            }
            EncabezadoConSello(sello) {
                Text("Última copia verificada", style = t.label, color = c.textSecondary)
                Text(
                    copia.cuando,
                    style = serifMediana,
                    color = c.textPrimary,
                    modifier = Modifier
                        .padding(top = Spacing.s0_5)
                        .semantics { if (copiaAlDia != null) contentDescription = "${copia.cuando}. ${selloDeLaCopia(copiaAlDia)}." },
                )
            }
            val detalles = listOfNotNull(copia.detalle, if (copiaAlDia == false && copia.sinCopia) "Haz una ahora." else null)
            detalles.forEach { detalle ->
                Text(detalle, style = t.small, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s1))
            }
        },
        matriz = {
            Text(
                "Es un archivo cifrado con tu contraseña maestra actual: fuera del teléfono, su seguridad es la de " +
                    "esa contraseña. Los códigos 2FA van dentro, cifrados: para abrirlos en otro teléfono hará falta " +
                    "también tu código de recuperación.",
                style = t.small,
                color = c.textSecondary,
            )
            Text(
                "Pide confirmación y tu huella o contraseña maestra; el archivo se relee y se verifica.",
                style = t.small,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.s2),
            )
            BotonPrimario(
                "Exportar copia",
                onExport,
                Modifier.fillMaxWidth().padding(top = Spacing.s3),
                enabled = !busy,
                // Con la letra grande la etiqueta necesita todo el ancho del botón: sin icono.
                icono = if (LocalDensity.current.fontScale > 1.3f) null else R.drawable.ic_copia,
            )
        },
    )
    Renglones(Modifier.padding(top = Spacing.s2)) {
        Fila(
            "Restaurar copia",
            descripcion = "Sustituye todo el contenido actual por el de la copia. Pide la contraseña maestra actual " +
                "y la de la copia.",
            enabled = !busy,
            onClick = onRestore,
        )
        // Como en el fichero: la bóveda anterior es un aviso con sus dos acciones; cada una pasa por su confirmación.
        Aparece(canUndoRestore) {
            Aviso(
                TipoAviso.Info,
                "Se conserva la bóveda anterior",
                mensaje = "La que estaba en su sitio antes de la última restauración o del último cambio. Volver a " +
                    "ella la deja bloqueada y sin huella, guarda la de ahora en su lugar y pide la contraseña " +
                    "maestra; descartarla la borra para siempre.",
                accion = "Volver a la anterior",
                onAccion = onUndoRestore,
                accionSecundaria = "Descartarla",
                onAccionSecundaria = onDiscardUndo,
                accionesActivas = !busy,
                modifier = Modifier.padding(top = Spacing.s2),
            )
        }
    }
}

/** La palabra del sello del resguardo. */
private fun selloDeLaCopia(alDia: Boolean) = if (alDia) TipoAviso.Exito.palabra else TipoAviso.Aviso.palabra

@Composable
private fun AparienciaApartado(
    apariencia: AjustesApariencia,
    onPersonalidad: (Personalidad) -> Unit,
    onTema: (Tema) -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Apartado("Apariencia", numero = "V")
    Text(
        "Personalidad",
        style = t.bodyStrong,
        color = c.textPrimary,
        modifier = Modifier.padding(top = Spacing.s2).semantics { heading() },
    )
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
    Text(
        "Tema",
        style = t.bodyStrong,
        color = c.textPrimary,
        modifier = Modifier.padding(top = Spacing.s6).semantics { heading() },
    )
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
private fun Privacidad(
    deviceSecure: Boolean,
    deviceKeySecurityLevel: String,
    deviceKeyWarning: String?,
    kdfUpgradeWarning: String?,
) {
    Apartado("Privacidad", numero = "VI")
    CertificadoDeLaCasa(
        nivelClave = deviceKeySecurityLevel,
        // El sello «Conforme» solo se estampa si no hay nada pendiente en Seguridad.
        conforme = deviceSecure && deviceKeyWarning == null && kdfUpgradeWarning == null &&
            deviceKeySecurityLevel in NIVELES_DE_HARDWARE,
        modifier = Modifier.padding(top = Spacing.s2),
    )
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
            painter = painterResource(R.drawable.logotipo_horizontal),
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
 * la chapa atornillada a una caja fuerte. Si la clave es solo de software, la placa lleva el sello
 * «Urgente» y el borde de peligro (🔒 el aviso sigue en color de error, como en el original) y dice
 * el riesgo en lugar de la explicación general.
 */
@Composable
private fun PlacaClave(nivel: String, aviso: String?, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val donde = nivel.replaceFirstChar { it.uppercase() }
    Ficha(modifier, borde = if (aviso != null) tintaDe(TipoAviso.Peligro) else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.bgSunken),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (aviso == null) R.drawable.ic_escudo else R.drawable.ic_alerta),
                    contentDescription = null,
                    tint = if (aviso == null) c.textLink else c.textSecondary,
                    modifier = Modifier.size(Sizes.iconLg),
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Dónde se guarda la clave de este teléfono", style = t.label, color = c.textSecondary)
                val nivelTexto = @Composable { modificador: Modifier ->
                    Text(
                        donde,
                        style = serifMediana,
                        color = c.textPrimary,
                        modifier = modificador
                            .semantics { if (aviso != null) contentDescription = "${TipoAviso.Peligro.palabra}. $donde" },
                    )
                }
                val sello = @Composable {
                    Sello(TipoAviso.Peligro.palabra, tintaDe(TipoAviso.Peligro), Modifier.clearAndSetSemantics { }, girado = 6f)
                }
                if (aviso != null && LocalDensity.current.fontScale > 1.3f) {
                    // Con la letra grande el sello no cabe al lado sin partir «Software»: se estampa debajo.
                    nivelTexto(Modifier.padding(top = Spacing.s0_5))
                    Box(Modifier.padding(top = Spacing.s1)) { sello() }
                } else {
                    Row(
                        modifier = Modifier.padding(top = Spacing.s0_5),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                    ) {
                        nivelTexto(Modifier.weight(1f, fill = false))
                        if (aviso != null) sello()
                    }
                }
            }
        }
        LineaPunteada(Modifier.padding(vertical = Spacing.s3))
        if (aviso != null) {
            Text(
                aviso,
                style = t.small,
                color = c.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            Text(
                "Es la capa que impide abrir una copia de los archivos de la app fuera de este teléfono.",
                style = t.small,
                color = c.textSecondary,
            )
        }
    }
}

/**
 * La fila de la frase antiphishing: la frase va en Young Serif y entre comillas, como en la nota del
 * desbloqueo, para que se reconozca al abrir. Mismas medidas que [Fila].
 */
@Composable
private fun FilaFrase(frase: String?, enabled: Boolean, onClick: () -> Unit) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .desbordar(Spacing.s2)
            .clip(ContrasenoraShapes.sm)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.s2, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Frase antiphishing", style = t.bodyStrong, color = if (enabled) c.textPrimary else c.textDisabled)
            if (frase != null) {
                Text(
                    "«$frase»",
                    style = serifMediana,
                    color = if (enabled) c.textPrimary else c.textDisabled,
                    modifier = Modifier.padding(top = Spacing.s1, bottom = Spacing.s1),
                )
            } else {
                Text(
                    "Sin frase",
                    style = t.small.copy(fontWeight = FontWeight.Medium),
                    color = if (enabled) c.textLink else c.textDisabled,
                )
            }
            Text(
                if (frase == null) {
                    "Elige una: la pantalla de desbloqueo la mostrará siempre antes de pedir la contraseña maestra y " +
                        "una app que la imite no la conocerá. Elegirla pide la contraseña maestra."
                } else {
                    "Si al desbloquear no la ves, no escribas la contraseña maestra. Cambiarla pide la contraseña maestra."
                },
                style = t.small,
                color = if (enabled) c.textSecondary else c.textDisabled,
            )
        }
        Icon(
            painterResource(R.drawable.ic_flecha),
            contentDescription = null,
            tint = if (enabled) c.textTertiary else c.textDisabled,
            modifier = Modifier.size(Sizes.iconMd),
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
 * Cada una lleva su casilla de impreso, que se marca de un trazo al elegirla, y la elegida un borde
 * ciruela de 2 dp; TalkBack la lee como botón de opción.
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
            Redondel(elegida, Modifier.align(Alignment.TopEnd).padding(Spacing.s4))
        }
    }
}

/**
 * Las garantías de la casa como un certificado: cada una, un renglón «qué ····· cómo» con su línea
 * de puntos de guía, y el sello «Conforme» estampado arriba.
 */
@Composable
private fun CertificadoDeLaCasa(nivelClave: String, conforme: Boolean, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Ficha(modifier) {
        EncabezadoConSello(
            sello = if (conforme) {
                { Sello(TipoAviso.Exito.palabra, tintaDe(TipoAviso.Exito), Modifier.clearAndSetSemantics { }, girado = 6f) }
            } else {
                null
            },
        ) {
            Text(
                "Garantías de la casa",
                style = serifMediana,
                color = c.textPrimary,
                modifier = Modifier.semantics {
                    contentDescription = if (conforme) "${TipoAviso.Exito.palabra}. Garantías de la casa" else "Garantías de la casa"
                },
            )
        }
        LineaPunteada(Modifier.padding(top = Spacing.s3, bottom = Spacing.s1))
        RenglonGuia("Conexión a Internet", "Sin permiso")
        RenglonGuia("Cifrado", "AES-256-GCM")
        RenglonGuia("Derivación de la clave", "Argon2id")
        RenglonGuia("Capa de hardware", capaDeHardware(nivelClave))
        RenglonGuia("Capturas de pantalla", "Bloqueadas")
        RenglonGuia("Copias en la nube", "Desactivadas")
        RenglonGuia("Autorrelleno de terceros", "Bloqueado")
        Text(
            if (nivelClave in NIVELES_DE_HARDWARE) {
                "Sin ese permiso, Android no deja que la app abra ninguna conexión. La capa de hardware va ligada al " +
                    "chip de seguridad del teléfono; los parámetros de la derivación están en «Derivación de la clave»."
            } else {
                "Sin ese permiso, Android no deja que la app abra ninguna conexión. En este teléfono la capa de " +
                    "hardware no está confirmada: lo explica Seguridad, en «Dónde se guarda la clave de este teléfono»."
            },
            style = t.small,
            color = c.textSecondary,
            modifier = Modifier.padding(top = Spacing.s3),
        )
    }
}

/** Niveles del Android Keystore que guardan la clave del teléfono en hardware. */
private val NIVELES_DE_HARDWARE = setOf(KeySecurityLevel.STRONGBOX.label, KeySecurityLevel.TEE.label)

/** Lo que dice el certificado de la capa de hardware según dónde guarda el Keystore la clave. */
private fun capaDeHardware(nivel: String): String = when (nivel) {
    KeySecurityLevel.STRONGBOX.label -> "StrongBox"
    KeySecurityLevel.TEE.label -> "Android Keystore (TEE)"
    KeySecurityLevel.SOFTWARE.label -> "Solo software"
    else -> "Sin confirmar"
}

/**
 * Renglón de impreso: la etiqueta a la izquierda, el valor a la derecha y una línea de puntos de guía
 * entre los dos, sobre la línea base del texto. Si no caben en un renglón (letra grande), la etiqueta
 * va arriba y la guía lleva al valor en el renglón de abajo. TalkBack lo lee de una vez.
 */
@Composable
private fun RenglonGuia(etiqueta: String, valor: String) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Layout(
        content = {
            Text(etiqueta, style = t.small, color = c.textSecondary)
            LineaPunteada(color = c.borderDefault)
            Text(valor, style = t.bodyStrong, color = c.textPrimary, textAlign = TextAlign.End)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s2)
            .semantics(mergeDescendants = true) { },
    ) { medibles, restricciones ->
        val (etiquetaM, guiaM, valorM) = medibles
        val ancho = restricciones.maxWidth
        val hueco = Spacing.s2.roundToPx()
        val guiaMinima = Spacing.s6.roundToPx()
        val libres = restricciones.copy(minWidth = 0, minHeight = 0)
        val anchoEtiqueta = etiquetaM.maxIntrinsicWidth(Constraints.Infinity)
        val anchoValor = valorM.maxIntrinsicWidth(Constraints.Infinity)
        if (anchoEtiqueta + anchoValor + 2 * hueco + guiaMinima <= ancho) {
            val e = etiquetaM.measure(libres.copy(maxWidth = anchoEtiqueta))
            val v = valorM.measure(libres.copy(maxWidth = anchoValor))
            val g = guiaM.measure(Constraints.fixedWidth(ancho - e.width - v.width - 2 * hueco))
            val base = maxOf(e[FirstBaseline], v[FirstBaseline])
            val alto = maxOf(base - e[FirstBaseline] + e.height, base - v[FirstBaseline] + v.height)
            layout(ancho, alto) {
                e.place(0, base - e[FirstBaseline])
                v.place(ancho - v.width, base - v[FirstBaseline])
                g.place(e.width + hueco, base - g.height)
            }
        } else {
            val e = etiquetaM.measure(libres)
            val v = valorM.measure(libres.copy(maxWidth = (ancho - hueco - guiaMinima).coerceAtLeast(0)))
            val g = guiaM.measure(Constraints.fixedWidth((ancho - v.width - hueco).coerceAtLeast(0)))
            layout(ancho, e.height + v.height) {
                e.place(0, 0)
                v.place(ancho - v.width, e.height)
                g.place(0, e.height + v[FirstBaseline] - g.height)
            }
        }
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

/** La última copia, partida para el resguardo: cuándo, lo que ha pasado desde entonces y si no hay ninguna. */
private class PartesDeLaCopia(val cuando: String, val detalle: String?, val sinCopia: Boolean)

/**
 * «Última copia: 3 oct 2026, 21:40. 2 cambios sin copiar desde entonces.» → «3 oct 2026, 21:40» y lo
 * demás, para el resguardo, que ya dice «Última copia verificada». Se corta por el último punto: la
 * fecha puede llevar los suyos («3 oct. 2026» en otros idiomas). «Última copia: nunca.» → «Nunca»;
 * «Última copia verificada con esta versión: ninguna.» → «Ninguna», con la versión como detalle. Si
 * el texto no empieza así, entero.
 */
private fun partesDeLaCopia(texto: String): PartesDeLaCopia {
    val deEstaVersion = "Última copia verificada con esta versión: "
    if (texto.startsWith(deEstaVersion)) {
        val cuando = texto.removePrefix(deEstaVersion).removeSuffix(".").replaceFirstChar { it.uppercase() }
        return PartesDeLaCopia(cuando, "No consta ninguna verificada con esta versión.", sinCopia = true)
    }
    val sinPrefijo = texto.removePrefix("Última copia: ").replaceFirstChar { it.uppercase() }
    val corte = sinPrefijo.lastIndexOf(". ")
    return if (corte < 0) {
        PartesDeLaCopia(sinPrefijo.removeSuffix("."), null, sinCopia = true)
    } else {
        PartesDeLaCopia(sinPrefijo.substring(0, corte), sinPrefijo.substring(corte + 2), sinCopia = false)
    }
}

/**
 * Un encabezado con su sello: el sello arriba a la derecha; con la letra grande no cabe al lado sin
 * partir las palabras, así que se estampa debajo.
 */
@Composable
private fun EncabezadoConSello(sello: (@Composable () -> Unit)?, contenido: @Composable ColumnScope.() -> Unit) {
    when {
        sello == null -> Column(Modifier.fillMaxWidth(), content = contenido)
        LocalDensity.current.fontScale > 1.3f -> Column(Modifier.fillMaxWidth()) {
            contenido()
            Box(Modifier.padding(top = Spacing.s2)) { sello() }
        }
        else -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Column(Modifier.weight(1f), content = contenido)
            sello()
        }
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
        text = { CodigoRecuperacionCampos(typed, onTypedChange = { typed = it }, busy = busy) },
        confirmButton = { BotonFantasma("Comprobar", { onConfirm(typed) }, enabled = !busy && typed.isNotBlank()) },
        dismissButton = { BotonFantasma("Cancelar", onDismiss) },
    )
}

/** El cuerpo del diálogo de comprobar el código de recuperación: qué pasará y el campo del código. */
@Composable
internal fun CodigoRecuperacionCampos(typed: String, onTypedChange: (String) -> Unit, busy: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        Text(
            "Escribe el código tal y como lo apuntaste. Contraseñora solo te dirá si es el correcto.",
            style = ContrasenoraTheme.type.body,
            color = ContrasenoraTheme.colors.textSecondary,
        )
        CampoCodigo(
            value = typed,
            onValueChange = onTypedChange,
            label = "Código de recuperación",
            placeholder = "XXXXX-XXXXX-XXXXX-XXXXX",
            enabled = !busy,
        )
    }
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
