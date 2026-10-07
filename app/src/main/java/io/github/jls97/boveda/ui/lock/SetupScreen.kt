package io.github.jls97.boveda.ui.lock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jls97.boveda.R
import io.github.jls97.boveda.data.ANTI_PHISHING_MAX_LENGTH
import io.github.jls97.boveda.data.ANTI_PHISHING_MIN_LENGTH
import io.github.jls97.boveda.data.AntiPhishingPhrase
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.ui.components.Apartado
import io.github.jls97.boveda.ui.components.Aviso
import io.github.jls97.boveda.ui.components.BotonFantasma
import io.github.jls97.boveda.ui.components.BotonPrimario
import io.github.jls97.boveda.ui.components.FilaCasilla
import io.github.jls97.boveda.ui.components.Isotipo
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.OpenLocalDocument
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.PasswordPromptDialog
import io.github.jls97.boveda.ui.components.StrengthMeter
import io.github.jls97.boveda.ui.components.TextoError
import io.github.jls97.boveda.ui.components.TipoAviso
import io.github.jls97.boveda.ui.components.Trabajando
import io.github.jls97.boveda.ui.components.hasSecureLockScreen
import io.github.jls97.boveda.ui.components.margenLateral
import io.github.jls97.boveda.ui.components.readBackup
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.voz
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

    AltaContenido(
        password = password,
        onPasswordChange = { password = it },
        confirmation = confirmation,
        onConfirmationChange = { confirmation = it },
        phrase = phrase,
        onPhraseChange = { if (it.length <= ANTI_PHISHING_MAX_LENGTH) phrase = it },
        understood = understood,
        onUnderstoodChange = { understood = it },
        busy = ui.busy,
        error = ui.error,
        onCreate = {
            if (!context.hasSecureLockScreen()) {
                viewModel.showError(VaultSession.SECURE_LOCK_SCREEN_REQUIRED)
            } else {
                viewModel.createVault(password, confirmation, phrase, phrases)
            }
        },
        onRestore = {
            // La misma comprobación que «Crear bóveda»: la clave de hardware de la bóveda
            // restaurada también depende del bloqueo de pantalla (B-43).
            if (!context.hasSecureLockScreen()) {
                viewModel.showError(VaultSession.SECURE_LOCK_SCREEN_REQUIRED)
            } else {
                viewModel.expectExternalActivity()
                openBackup.launch(arrayOf("*/*"))
            }
        },
    )

    pendingBackup?.let { backup ->
        PasswordPromptDialog(
            title = "Restaurar copia",
            text = "Escribe la contraseña maestra con la que se hizo la copia.",
            confirmLabel = "Restaurar",
            onConfirm = { backupPassword ->
                pendingBackup = null
                viewModel.restoreBackupFirstRun(backup, backupPassword)
            },
            onDismiss = { pendingBackup = null },
        )
    }
}

/**
 * Lo que se ve al estrenar la app, sin estado: la bienvenida de la Contraseñora y un impreso en dos
 * apartados, la contraseña maestra y la frase antiphishing, antes de crear la bóveda.
 */
@Composable
internal fun AltaContenido(
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmation: String,
    onConfirmationChange: (String) -> Unit,
    phrase: String,
    onPhraseChange: (String) -> Unit,
    understood: Boolean,
    onUnderstoodChange: (Boolean) -> Unit,
    busy: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onRestore: () -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val margen = margenLateral()
    // Se avisa en cuanto la repetición deja de ser el principio de la contraseña, no letra a letra.
    val noCoincide = confirmation.isNotEmpty() && !password.startsWith(confirmation)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .testTag("setup_screen")
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(start = margen, end = margen, top = Spacing.s10, bottom = Spacing.s8),
        ) {
            Isotipo(Modifier.size(72.dp), descripcion = "Contraseñora")
            Text(
                voz("Hola, cielo. Yo me encargo de tus claves.", "Te damos la bienvenida a Contraseñora"),
                style = t.display2,
                color = c.textPrimary,
                modifier = Modifier.padding(top = Spacing.s6).semantics { heading() },
            )
            Text(
                voz(
                    "Guardo tus contraseñas y tus códigos 2FA, y a mí no me la cuela nadie. Todo se cifra en " +
                        "este teléfono y de aquí no sale: ni siquiera tengo permiso de Internet.",
                    "Guarda tus contraseñas y códigos 2FA. Todo se cifra en este teléfono y no sale de él: la " +
                        "app no tiene permiso de Internet.",
                ),
                style = t.bodyLarge,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.s3),
            )

            Apartado("La contraseña maestra", numero = "I")
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                Text(
                    voz(
                        "Es la única que tienes que recordar. Con ella cifro tus claves y no la guardo en ningún " +
                            "sitio: si la olvidas, no puedo recuperarla. Ni yo sé cuál es. Así de discreta soy.",
                        "Es la única que necesitas recordar. Cifra tus contraseñas en este teléfono y no se guarda " +
                            "en ningún sitio: si la olvidas, nadie puede recuperarla.",
                    ),
                    style = t.body,
                    color = c.textPrimary,
                )
                Aviso(
                    TipoAviso.Info,
                    voz("Un consejo de la casa", "Consejo"),
                    mensaje = "Una frase de 4 o 5 palabras que no tengan relación, con algún número o símbolo, " +
                        "es fácil de recordar y muy difícil de adivinar.",
                )
                PasswordField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = "Contraseña maestra",
                    enabled = !busy,
                )
                StrengthMeter(password)
                PasswordField(
                    value = confirmation,
                    onValueChange = onConfirmationChange,
                    label = "Repite la contraseña maestra",
                    imeAction = ImeAction.Done,
                    enabled = !busy,
                    error = if (noCoincide) "No coincide con la de arriba." else null,
                )
            }

            Apartado("Tu frase antiphishing", numero = "II")
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                Text(
                    "Elige una frase corta que solo tú conozcas. Contraseñora la mostrará siempre antes de pedirte " +
                        "la contraseña maestra, también cuando rellene en otras apps. Una app que imite esta " +
                        "pantalla no la conoce: si no ves tu frase, no escribas la contraseña.",
                    style = t.body,
                    color = c.textPrimary,
                )
                NoLearningTextField(
                    value = phrase,
                    onValueChange = onPhraseChange,
                    label = "Frase antiphishing",
                    placeholder = "p. ej. Las lentejas de los jueves",
                    ayuda = "De $ANTI_PHISHING_MIN_LENGTH a $ANTI_PHISHING_MAX_LENGTH caracteres. No cifra nada y " +
                        "podrás cambiarla en Ajustes.",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    enabled = !busy,
                )
            }

            FilaCasilla(
                "Entiendo que si olvido la contraseña maestra perderé el acceso a mis datos.",
                marcada = understood,
                onCambio = onUnderstoodChange,
                enabled = !busy,
                modifier = Modifier.padding(top = Spacing.s6),
            )
            if (error != null) TextoError(error, Modifier.padding(top = Spacing.s3))
            if (busy) {
                Trabajando(
                    voz("Cifrando la bóveda. La seguridad no tiene prisa…", "Cifrando la bóveda…"),
                    Modifier.padding(top = Spacing.s5),
                )
            } else {
                BotonPrimario(
                    "Crear bóveda",
                    onCreate,
                    Modifier.fillMaxWidth().padding(top = Spacing.s5),
                    enabled = understood && password.isNotEmpty() && confirmation.isNotEmpty() && phrase.isNotBlank(),
                    icono = R.drawable.ic_candado,
                )
            }

            LineaPunteada(Modifier.padding(top = Spacing.s10, bottom = Spacing.s3))
            Text("¿Vienes de otro teléfono?", style = t.small, color = c.textTertiary)
            BotonFantasma(
                "Restaurar una copia de seguridad",
                onRestore,
                enabled = !busy,
                icono = R.drawable.ic_deshacer,
                modifier = Modifier.padding(top = Spacing.s1),
            )
        }
    }
}
