package io.github.jls97.boveda.ui.otp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.core.otp.OtpAlgorithm
import io.github.jls97.boveda.core.otp.OtpSecret
import io.github.jls97.boveda.core.otp.Totp
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.session.OtpAccess
import io.github.jls97.boveda.ui.components.BackButton
import io.github.jls97.boveda.ui.components.ChoiceDialog
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.findActivity
import kotlinx.coroutines.delay
import javax.crypto.Cipher

private val ScreenSpacing = Arrangement.spacedBy(12.dp)

/** Shows the fingerprint prompt for [cipher]; [onAuthorized] gets the cipher it unlocked. */
private fun askFingerprint(
    context: Context,
    otp: OtpViewModel,
    title: String,
    subtitle: String,
    cipher: Cipher?,
    onAuthorized: (Cipher) -> Unit,
) {
    if (cipher == null) return
    val activity = context.findActivity() ?: return
    BiometricPrompts.authenticate(activity, title, subtitle, cipher, negativeLabel = "Cancelar") { authorized, error ->
        when {
            authorized != null -> onAuthorized(authorized)
            error != null -> otp.message(error)
        }
    }
}

// region Entry detail

/** The 2FA part of an entry: add a code, or show and copy it after a fingerprint. */
@Composable
fun OtpCard(
    entry: VaultEntry,
    otpAccess: OtpAccess,
    otp: OtpViewModel,
    onAdd: () -> Unit,
    onRecover: () -> Unit,
) {
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }
    val revealed = otp.revealed?.takeIf { it.entryId == entry.id }

    // Leaving the entry hides its code.
    DisposableEffect(entry.id) { onDispose { otp.hide(entry.id) } }

    fun open(copy: Boolean) {
        askFingerprint(context, otp, "Código 2FA", entry.title, otp.unlockCipher()) { authorized ->
            otp.reveal(authorized, entry.id, copy)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Código 2FA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            val sealed = entry.otp
            when {
                sealed == null -> {
                    Text(
                        "Si esta cuenta usa una app de autenticación, guarda aquí su código. Solo se abrirá con tu huella.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CardActions {
                        TextButton(onClick = onAdd, enabled = !otp.busy) { Text("Añadir código 2FA") }
                    }
                }
                otpAccess == OtpAccess.LOCKED -> {
                    Text(
                        "Bloqueado en este móvil. Recupéralo con tu código de recuperación.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    CardActions {
                        TextButton(onClick = { confirmRemove = true }, enabled = !otp.busy) { Text("Quitar") }
                        TextButton(onClick = onRecover, enabled = !otp.busy) { Text("Recuperar") }
                    }
                }
                otpAccess == OtpAccess.NONE -> {
                    Text(
                        "No se puede abrir: a la bóveda le falta la llave de los códigos 2FA.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    CardActions {
                        TextButton(onClick = { confirmRemove = true }, enabled = !otp.busy) { Text("Quitar") }
                    }
                }
                revealed != null -> {
                    LiveCode(revealed.secret, stillShown = { !revealed.expired }, onExpired = { otp.hide(entry.id) })
                    CardActions {
                        TextButton(onClick = { otp.hide(entry.id) }) { Text("Ocultar") }
                        TextButton(onClick = otp::copyCode) { Text("Copiar") }
                    }
                }
                else -> {
                    Text("••• •••", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                    Text(
                        "Protegido con tu huella",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CardActions {
                        TextButton(onClick = { confirmRemove = true }, enabled = !otp.busy) { Text("Quitar") }
                        TextButton(onClick = { open(copy = false) }, enabled = !otp.busy) { Text("Mostrar") }
                        TextButton(onClick = { open(copy = true) }, enabled = !otp.busy) { Text("Copiar") }
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "¿Quitar el código 2FA?",
            text = "Se borrará de «${entry.title}». Antes de hacerlo, asegúrate de haber desactivado la verificación " +
                "en dos pasos en la web o de tener otra forma de generar sus códigos: si no, podrías quedarte sin acceso.",
            confirmLabel = "Quitar",
            onConfirm = {
                confirmRemove = false
                otp.remove(entry.id)
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun CardActions(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * The current code of [secret], its label and a countdown, refreshed every second until
 * [stillShown] says otherwise. [onExpired] then wipes the secret, so it is checked before reading.
 */
@Composable
private fun LiveCode(secret: OtpSecret, stillShown: () -> Boolean = { true }, onExpired: () -> Unit = {}) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(secret) {
        while (true) {
            if (!stillShown()) {
                onExpired()
                break
            }
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    val secondsLeft = secret.secondsLeft(now)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (secret.label.isNotEmpty()) {
            Text(secret.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Totp.format(secret.code(now)), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
        LinearProgressIndicator(
            progress = { secondsLeft / secret.params.period.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Cambia en $secondsLeft s", style = MaterialTheme.typography.bodySmall)
    }
}

// endregion

// region Add a code

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpAddScreen(
    entry: VaultEntry,
    otpAccess: OtpAccess,
    otp: OtpViewModel,
    onScan: () -> Unit,
    onNeedsSetup: () -> Unit,
    onNeedsRecovery: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<String?>(null) }
    val pending = otp.pending
    val inputError = otp.inputError

    fun leave() {
        otp.clearDraft()
        onBack()
    }
    BackHandler { leave() }

    fun save() {
        when (otpAccess) {
            OtpAccess.NONE ->
                if (BiometricPrompts.isStrongBiometricAvailable(context)) {
                    otp.beginRecoveryCode()
                    onNeedsSetup()
                } else {
                    otp.message("Registra una huella en los ajustes del teléfono: los códigos 2FA solo se abren con ella.")
                }
            OtpAccess.LOCKED -> {
                otp.message("Primero recupera en este móvil los códigos 2FA que ya tienes.")
                onNeedsRecovery()
            }
            OtpAccess.READY ->
                askFingerprint(context, otp, "Guardar código 2FA", entry.title, otp.unlockCipher()) { authorized ->
                    otp.add(authorized, onSaved)
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Añadir código 2FA") },
                navigationIcon = { BackButton(::leave) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = ScreenSpacing,
        ) {
            Text(
                "Para «${entry.title}». Al activar la verificación en dos pasos, la web te enseña un código QR y, " +
                    "casi siempre, una clave de texto debajo. Usa cualquiera de los dos.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onScan, enabled = !otp.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Escanear el código QR")
            }
            OutlinedTextField(
                value = otp.input,
                onValueChange = otp::updateInput,
                label = { Text("Clave de configuración") },
                placeholder = { Text("p. ej. JBSW Y3DP EHPK 3PXP") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                // A password keyboard doesn't learn or suggest what is typed.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                ),
                isError = inputError != null,
                supportingText = if (inputError != null) {
                    { Text(otpInputErrorText(inputError)) }
                } else {
                    null
                },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!otp.inputIsLink) {
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "Ocultar opciones avanzadas" else "Opciones avanzadas")
                }
                if (showAdvanced) {
                    Text(
                        "Cámbialas solo si la web lo indica.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val params = otp.manualParams
                    ListItem(
                        headlineContent = { Text("Algoritmo") },
                        supportingContent = { Text(params.algorithm.label) },
                        modifier = Modifier.clickable { choosing = "algorithm" },
                    )
                    ListItem(
                        headlineContent = { Text("Cifras") },
                        supportingContent = { Text("${params.digits}") },
                        modifier = Modifier.clickable { choosing = "digits" },
                    )
                    ListItem(
                        headlineContent = { Text("Cambia cada") },
                        supportingContent = { Text("${params.period} segundos") },
                        modifier = Modifier.clickable { choosing = "period" },
                    )
                }
            }
            if (pending != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Código actual", style = MaterialTheme.typography.labelMedium)
                        LiveCode(pending)
                        Text(
                            "Si la web te pide un código para confirmar la activación, escribe este.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Text(
                "Los códigos dependen de la hora del móvil: déjala en automática.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = ::save, enabled = pending != null && !otp.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar con mi huella")
            }
        }
    }

    when (choosing) {
        "algorithm" -> ChoiceDialog(
            title = "Algoritmo",
            options = OtpAlgorithm.entries.map { it to it.label },
            selected = otp.manualParams.algorithm,
            onSelect = {
                choosing = null
                otp.updateParams(otp.manualParams.copy(algorithm = it))
            },
            onDismiss = { choosing = null },
        )
        "digits" -> ChoiceDialog(
            title = "Cifras",
            options = listOf(6, 7, 8).map { it to "$it cifras" },
            selected = otp.manualParams.digits,
            onSelect = {
                choosing = null
                otp.updateParams(otp.manualParams.copy(digits = it))
            },
            onDismiss = { choosing = null },
        )
        "period" -> ChoiceDialog(
            title = "Cambia cada",
            options = listOf(30, 60).map { it to "$it segundos" },
            selected = otp.manualParams.period,
            onSelect = {
                choosing = null
                otp.updateParams(otp.manualParams.copy(period = it))
            },
            onDismiss = { choosing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScanScreen(onScanned: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var permitted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
    }
    LaunchedEffect(Unit) {
        if (!permitted && !asked) {
            asked = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escanear código QR") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = ScreenSpacing,
        ) {
            when {
                cameraFailed -> {
                    Text("No se pudo abrir la cámara. Cierra otras apps que la estén usando o escribe la clave a mano.")
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Escribir la clave") }
                }
                permitted -> {
                    Text("Apunta al código QR que te enseña la web al activar la verificación en dos pasos.")
                    QrCameraPreview(
                        onDecoded = { text ->
                            val trimmed = text.trim()
                            if (!accepted && trimmed.startsWith("otpauth://", ignoreCase = true)) {
                                accepted = true
                                onScanned(trimmed)
                            } else if (!accepted) {
                                hint = "Ese QR no es de verificación en dos pasos. Busca el que aparece al activarla."
                            }
                        },
                        onError = { cameraFailed = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                    hint?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(
                        "La imagen se analiza en el teléfono y no se guarda en ningún sitio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        "Bóveda solo usa la cámara aquí, para leer el código QR. Si no te pregunta, da el permiso en " +
                            "Ajustes → Apps → Bóveda → Permisos, o escribe la clave a mano.",
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Permitir la cámara")
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Escribir la clave") }
                }
            }
        }
    }
}

// endregion

// region Recovery code

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryCodeScreen(
    purpose: RecoveryCodePurpose,
    otp: OtpViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    var typed by remember { mutableStateOf("") }
    val matches = remember(typed, otp.recoveryCodeText) { otp.recoveryCodeMatches(typed) }

    fun leave() {
        otp.clearRecoveryCode()
        onBack()
    }
    BackHandler { leave() }

    fun confirm() {
        when (purpose) {
            RecoveryCodePurpose.SETUP ->
                askFingerprint(context, otp, "Proteger códigos 2FA", "Tu huella abrirá cada código", otp.enrollmentCipher()) {
                    otp.setUp(it, onDone)
                }
            RecoveryCodePurpose.REPLACE -> {
                // Two fingerprints: one opens the current 2FA key, the other protects the new one.
                // The new Keystore key is created first; it lives in another slot, so the current one keeps working.
                val enrollment = otp.enrollmentCipher() ?: return
                askFingerprint(context, otp, "Nuevo código de recuperación", "Abre tus códigos con tu huella", otp.unlockCipher()) { unlock ->
                    askFingerprint(context, otp, "Nuevo código de recuperación", "Otra vez, para proteger la llave nueva", enrollment) {
                        otp.replaceRecoveryCode(unlock, it, onDone)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (purpose == RecoveryCodePurpose.SETUP) "Protege tus códigos 2FA" else "Nuevo código de recuperación")
                },
                navigationIcon = { BackButton(::leave) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = ScreenSpacing,
        ) {
            if (purpose == RecoveryCodePurpose.SETUP) {
                Text(
                    "Cada código 2FA se cifra con una llave que solo se abre con tu huella. Esa llave no sale de este " +
                        "móvil, así que para recuperar los códigos en otro teléfono (desde una copia) o si cambias tus " +
                        "huellas necesitarás este código de recuperación:",
                )
            } else {
                Text(
                    "Este código sustituirá al anterior y tus códigos 2FA se cifrarán con una llave nueva (te pedirá " +
                        "la huella dos veces). Las copias de seguridad que ya tengas seguirán necesitando el antiguo, " +
                        "así que haz una copia nueva después.",
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text(
                    otp.recoveryCodeText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                "Apúntalo en papel y guárdalo lejos del móvil. No lo guardes en Bóveda, en fotos ni en la nube: " +
                    "si alguien lo consigue junto a tu contraseña maestra, podría leer tus códigos sin tu huella. " +
                    "Si lo pierdes y pierdes el móvil, tendrás que usar los códigos de respaldo de cada web.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("Escríbelo para confirmar que lo has apuntado") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::confirm, enabled = matches && !otp.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (purpose == RecoveryCodePurpose.SETUP) "Activar con mi huella" else "Cambiar con mi huella")
            }
            if (otp.busy) Text("Cifrando…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpRecoverScreen(otp: OtpViewModel, onDone: () -> Unit, onBack: () -> Unit, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    var typed by remember { mutableStateOf("") }

    fun leave() {
        otp.clearCheckedRecoveryCode()
        onBack()
    }
    BackHandler { leave() }

    fun recover() {
        otp.checkRecoveryCode(typed) {
            askFingerprint(context, otp, "Recuperar códigos 2FA", "Tu huella abrirá cada código", otp.enrollmentCipher()) {
                otp.recover(it, onDone)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recuperar códigos 2FA") },
                navigationIcon = { BackButton(::leave) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = ScreenSpacing,
        ) {
            Text(
                "Tus códigos 2FA están en la bóveda, pero este móvil no tiene la llave de huella que los abre. " +
                    "Pasa al restaurar una copia, al estrenar móvil o al añadir o borrar una huella.",
            )
            Text("Escribe el código de recuperación que apuntaste al guardar tu primer código 2FA.")
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
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::recover, enabled = typed.isNotBlank() && !otp.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Recuperar con mi huella")
            }
            if (otp.busy) Text("Comprobando…", style = MaterialTheme.typography.bodySmall)
            Text(
                "Sin ese código no se pueden recuperar: tendrás que volver a activar la verificación en cada web " +
                    "con los códigos de respaldo que te dio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// endregion
