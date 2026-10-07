package io.github.jls97.boveda.ui.lock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jls97.boveda.R
import io.github.jls97.boveda.data.AntiPhishingPhrase
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.ui.components.Aviso
import io.github.jls97.boveda.ui.components.BotonFantasma
import io.github.jls97.boveda.ui.components.BotonPrimario
import io.github.jls97.boveda.ui.components.BotonSecundario
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.DescanseEnPass
import io.github.jls97.boveda.ui.components.Ficha
import io.github.jls97.boveda.ui.components.InsecureDeviceWarning
import io.github.jls97.boveda.ui.components.Isotipo
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.OpenLocalDocument
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.RestoreBackupDialog
import io.github.jls97.boveda.ui.components.SecureAlertDialog
import io.github.jls97.boveda.ui.components.Sello
import io.github.jls97.boveda.ui.components.TextoError
import io.github.jls97.boveda.ui.components.TipoAviso
import io.github.jls97.boveda.ui.components.Trabajando
import io.github.jls97.boveda.ui.components.findActivity
import io.github.jls97.boveda.ui.components.hasSecureLockScreen
import io.github.jls97.boveda.ui.components.margenLateral
import io.github.jls97.boveda.ui.components.partirEnAviso
import io.github.jls97.boveda.ui.components.readBackup
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.LocalPersonalidad
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Personalidad
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.YoungSerif
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import io.github.jls97.boveda.ui.theme.voz
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pantalla de desbloqueo, «¿Quién va?». [requestContext] es, en el autorrelleno, para quién se va a
 * rellenar (p. ej. «Para: com.ejemplo.app»); se muestra bajo el título para que la pantalla no sea
 * genérica (M-04). En la app principal queda a null.
 *
 * [onUsePasswordInApp], solo en el autorrelleno: con la huella activada, «Usar contraseña» no
 * despliega el campo en esta pantalla, que vive dentro de la tarea de la app que pide el relleno,
 * sino que abre Contraseñora desde su propia tarea y cierra esta (M-04). Así la contraseña maestra
 * no se teclea nunca encima de otra app mientras haya huella.
 *
 * [abriendo] es true mientras la pantalla se va porque la bóveda se acaba de abrir: el arco del
 * candado sube, el momento de marca de la guía.
 */
@Composable
fun UnlockScreen(
    viewModel: LockViewModel,
    allowRestore: Boolean = true,
    requestContext: String? = null,
    onUsePasswordInApp: (() -> Unit)? = null,
    abriendo: Boolean = false,
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

    /** «Usar contraseña»: en la app, despliega el campo; en el autorrelleno, abre la app (M-04). */
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
            "Desbloquear Contraseñora",
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

    DesbloqueoContenido(
        phrase = phrase,
        deviceSecure = deviceSecure,
        notice = ui.notice,
        error = ui.error,
        busy = ui.busy,
        wrongPasswords = ui.wrongPasswords,
        blockedSeconds = if (blocked) (ui.blockedUntil - now + 999) / 1_000 else null,
        biometricEnabled = biometricEnabled,
        usePassword = usePassword,
        password = password,
        onPasswordChange = { password = it },
        allowRestore = allowRestore,
        canUndoRestore = canUndoRestore,
        requestContext = requestContext,
        inAutofill = onUsePasswordInApp != null,
        abriendo = abriendo,
        onUnlock = { if (!blocked) viewModel.unlock(password) },
        onFingerprint = ::promptFingerprint,
        onUsePassword = ::usePasswordInstead,
        onRestore = { confirmRestore = true },
        onUndoRestore = { confirmUndo = true },
    )

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

/**
 * Lo que se ve de la pantalla de desbloqueo, sin estado: el isotipo, el título, para quién es (en el
 * autorrelleno), la frase antiphishing y la contraseña o la huella; durante el bloqueo temporal,
 * «Descanse en Pass» con su cuenta atrás. [blockedSeconds] es null si no hay bloqueo.
 */
@Composable
internal fun DesbloqueoContenido(
    phrase: String?,
    deviceSecure: Boolean,
    notice: String?,
    error: String?,
    busy: Boolean,
    wrongPasswords: Int,
    blockedSeconds: Long?,
    biometricEnabled: Boolean,
    usePassword: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
    allowRestore: Boolean,
    canUndoRestore: Boolean,
    requestContext: String?,
    inAutofill: Boolean,
    abriendo: Boolean,
    onUnlock: () -> Unit,
    onFingerprint: () -> Unit,
    onUsePassword: () -> Unit,
    onRestore: () -> Unit,
    onUndoRestore: () -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val sobria = LocalPersonalidad.current == Personalidad.Sobria
    val margen = margenLateral()
    val conHuella = biometricEnabled && !usePassword
    val blocked = blockedSeconds != null

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .testTag("unlock_screen")
            .safeDrawingPadding(),
    ) {
        val altoVisible = maxHeight
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    // Con poco contenido, el pie de «Restaurar» se queda abajo y no flotando a media pantalla.
                    .heightIn(min = altoVisible)
                    .padding(start = margen, end = margen, top = Spacing.s10, bottom = Spacing.s6),
            ) {
                AnimatedContent(
                    targetState = blocked,
                    transitionSpec = { fadeIn(tween(Motion.SLOW)) togetherWith fadeOut(tween(Motion.FAST)) },
                    label = "bloqueo temporal",
                ) { bloqueada ->
                    if (bloqueada) {
                        // Durante el bloqueo no se pide nada: solo la lápida y cuánto falta.
                        DescanseEnPass(
                            segundosRestantes = blockedSeconds ?: 0,
                            modifier = Modifier.padding(top = Spacing.s8),
                        )
                    } else {
                        Column {
                            IsotipoPortero(abriendo = abriendo, trabajando = busy, fallos = wrongPasswords)
                            Text(
                                voz("¿Quién va?", "Contraseñora está bloqueada"),
                                style = if (sobria) t.display2 else t.display1,
                                color = c.textPrimary,
                                modifier = Modifier.padding(top = Spacing.s6).semantics { heading() },
                            )
                            Text(
                                if (conHuella) {
                                    voz("Enséñame tu huella y te abro la ventanilla.", "Usa tu huella para ver tus claves.")
                                } else {
                                    voz(
                                        "Enséñame la contraseña maestra y te abro la ventanilla.",
                                        "Escribe la contraseña maestra para ver tus claves.",
                                    )
                                },
                                style = t.bodyLarge,
                                color = c.textSecondary,
                                modifier = Modifier.padding(top = Spacing.s2),
                            )
                            if (requestContext != null) {
                                ParaQuien(requestContext, Modifier.padding(top = Spacing.s5))
                            }
                            Column(
                                modifier = Modifier.padding(top = Spacing.s6),
                                verticalArrangement = Arrangement.spacedBy(Spacing.s4),
                            ) {
                                NotaAntiphishing(phrase)
                                if (!deviceSecure) InsecureDeviceWarning()
                                notice?.let { notice ->
                                    val (titulo, resto) = partirEnAviso(notice)
                                    Aviso(TipoAviso.Info, titulo, mensaje = resto)
                                }
                                if (usePassword) {
                                    PasswordField(
                                        value = password,
                                        onValueChange = onPasswordChange,
                                        label = "Contraseña maestra",
                                        imeAction = ImeAction.Done,
                                        onImeAction = onUnlock,
                                        enabled = !busy,
                                        error = error,
                                        intentosFallidos = wrongPasswords,
                                    )
                                } else {
                                    error?.let { TextoError(it) }
                                }
                                Acciones(
                                    busy = busy,
                                    usePassword = usePassword,
                                    biometricEnabled = biometricEnabled,
                                    canUnlock = password.isNotEmpty(),
                                    inAutofill = inAutofill,
                                    onUnlock = onUnlock,
                                    onFingerprint = onFingerprint,
                                    onUsePassword = onUsePassword,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (allowRestore) {
                    LineaPunteada(Modifier.padding(top = Spacing.s10, bottom = Spacing.s3))
                    Text("¿Vienes de otro teléfono o la bóveda no abre?", style = t.small, color = c.textTertiary)
                    BotonFantasma(
                        "Restaurar una copia de seguridad",
                        onRestore,
                        enabled = !busy,
                        icono = R.drawable.ic_deshacer,
                        modifier = Modifier.padding(top = Spacing.s1),
                    )
                    if (canUndoRestore) {
                        BotonFantasma("Volver a la bóveda anterior", onUndoRestore, enabled = !busy)
                    }
                }
            }
        }
    }
}

/**
 * El isotipo grande de la pantalla. Mientras se descifra, el arco sube y baja un poco, como quien
 * prueba la llave; si la contraseña no es, niega con la cabeza; al abrir, el arco sube del todo.
 */
@Composable
private fun IsotipoPortero(abriendo: Boolean, trabajando: Boolean, fallos: Int) {
    val reduced = rememberReducedMotion()
    val apertura = remember { Animatable(0f) }
    LaunchedEffect(abriendo, trabajando) {
        when {
            abriendo -> apertura.animateTo(1f, tween(Motion.BASE, easing = Motion.Emphasized))
            trabajando && !reduced -> while (true) {
                apertura.animateTo(0.35f, tween(520, easing = FastOutSlowInEasing))
                apertura.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
            }
            else -> apertura.animateTo(0f, tween(Motion.FAST))
        }
    }
    val negar = remember { Animatable(1f) }
    LaunchedEffect(fallos) {
        if (fallos > 0 && !reduced) {
            negar.snapTo(0f)
            negar.animateTo(1f, tween(Motion.LOCK_SHAKE))
        }
    }
    Isotipo(
        modifier = Modifier
            .size(84.dp)
            .graphicsLayer {
                val p = negar.value
                rotationZ = (sin(p * PI * 4) * 8.0 * (1 - p)).toFloat()
            },
        apertura = apertura.value,
        descripcion = "Contraseñora",
    )
}

/** Para quién es el desbloqueo en el autorrelleno: una línea en ficha, imposible de pasar por alto. */
@Composable
private fun ParaQuien(texto: String, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    Ficha(modifier, relleno = PaddingValues(horizontal = Spacing.s4, vertical = Spacing.s3), borde = c.borderStrong) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Icon(painterResource(R.drawable.ic_llave), contentDescription = null, tint = c.textLink, modifier = Modifier.size(Sizes.iconMd))
            Text(texto, style = ContrasenoraTheme.type.bodyStrong, color = c.textPrimary)
        }
    }
}

/**
 * La frase antiphishing como una nota con su sello, encima de la contraseña (M-04). Sin frase
 * (bóvedas creadas antes de existir), un aviso para elegirla: la pantalla sigue siendo genérica
 * hasta entonces.
 */
@Composable
private fun NotaAntiphishing(phrase: String?) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    if (phrase == null) {
        Aviso(
            TipoAviso.Aviso,
            "Esta bóveda no tiene frase antiphishing",
            mensaje = "Elígela en Ajustes, apartado Seguridad: Contraseñora la mostrará siempre aquí y, si " +
                "una app imita esta pantalla, no la conocerá.",
        )
        return
    }
    Ficha {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Tu frase antiphishing", style = t.label, color = c.textSecondary)
                Text(
                    "«$phrase»",
                    style = t.title2.copy(fontFamily = YoungSerif, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 31.sp),
                    color = c.textPrimary,
                    modifier = Modifier.padding(top = Spacing.s1),
                )
            }
            Sello("Nota", c.infoFg, Modifier.padding(start = Spacing.s2), girado = 6f)
        }
        Text(
            "Si no la ves, no escribas la contraseña.",
            style = t.small,
            color = c.textSecondary,
            modifier = Modifier.padding(top = Spacing.s2),
        )
    }
}

/** Botones del desbloqueo según haya huella y esté o no desplegada la contraseña. */
@Composable
private fun Acciones(
    busy: Boolean,
    usePassword: Boolean,
    biometricEnabled: Boolean,
    canUnlock: Boolean,
    inAutofill: Boolean,
    onUnlock: () -> Unit,
    onFingerprint: () -> Unit,
    onUsePassword: () -> Unit,
) {
    val c = ContrasenoraTheme.colors
    if (busy) {
        Trabajando(voz("Mirando si eres tú…", "Descifrando…"), Modifier.padding(vertical = Spacing.s3))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        if (usePassword) {
            BotonPrimario("Desbloquear", onUnlock, Modifier.fillMaxWidth(), enabled = canUnlock, icono = R.drawable.ic_candado)
        }
        if (biometricEnabled) {
            if (usePassword) {
                BotonSecundario("Usar huella", onFingerprint, Modifier.fillMaxWidth(), icono = R.drawable.ic_huella)
            } else {
                BotonPrimario("Usar huella", onFingerprint, Modifier.fillMaxWidth(), icono = R.drawable.ic_huella)
                BotonFantasma("Usar contraseña", onUsePassword, Modifier.fillMaxWidth())
                if (inAutofill) {
                    Text(
                        "Se abrirá Contraseñora: la contraseña maestra no se escribe en esta pantalla, que " +
                            "aparece encima de otra app.",
                        style = ContrasenoraTheme.type.small,
                        color = c.textSecondary,
                    )
                }
            }
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
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
            BotonFantasma(
                if (secondsLeft > 0) "Restaurar ($secondsLeft s)" else "Restaurar",
                onConfirm,
                enabled = confirmed,
                peligro = true,
            )
        },
        dismissButton = { BotonFantasma("Cancelar", onDismiss) },
    )
}
