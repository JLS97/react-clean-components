package io.github.jls97.boveda.autofill

import android.content.Intent
import android.service.autofill.Dataset
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jls97.boveda.BovedaApplication
import io.github.jls97.boveda.MainActivity
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.autofill.AutofillTarget
import io.github.jls97.boveda.core.autofill.CredentialMatcher
import io.github.jls97.boveda.core.autofill.ExternalText
import io.github.jls97.boveda.core.autofill.FillWarnings
import io.github.jls97.boveda.core.autofill.SaveCapture
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.session.OtpAccess
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import io.github.jls97.boveda.ui.components.Apartado
import io.github.jls97.boveda.ui.components.BarraSuperior
import io.github.jls97.boveda.ui.components.BotonFantasma
import io.github.jls97.boveda.ui.components.BotonIcono
import io.github.jls97.boveda.ui.components.BotonPrimario
import io.github.jls97.boveda.ui.components.BotonSecundario
import io.github.jls97.boveda.ui.components.CabeceraGrande
import io.github.jls97.boveda.ui.components.CampoBusqueda
import io.github.jls97.boveda.ui.components.EstadoVacio
import io.github.jls97.boveda.ui.components.Ficha
import io.github.jls97.boveda.ui.components.FilaCasilla
import io.github.jls97.boveda.ui.components.Isotipo
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.Mostrador
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.OnAppBackground
import io.github.jls97.boveda.ui.components.Pantalla
import io.github.jls97.boveda.ui.components.Redondel
import io.github.jls97.boveda.ui.components.Salida
import io.github.jls97.boveda.ui.components.Sello
import io.github.jls97.boveda.ui.components.TextoError
import io.github.jls97.boveda.ui.components.TipoAviso
import io.github.jls97.boveda.ui.components.Trabajando
import io.github.jls97.boveda.ui.components.desbordar
import io.github.jls97.boveda.ui.components.findActivity
import io.github.jls97.boveda.ui.components.margenDeLista
import io.github.jls97.boveda.ui.components.margenLateral
import io.github.jls97.boveda.ui.components.textoSecreto
import io.github.jls97.boveda.ui.components.tintaDe
import io.github.jls97.boveda.ui.lock.LockViewModel
import io.github.jls97.boveda.ui.lock.UnlockScreen
import io.github.jls97.boveda.ui.theme.Comportamiento
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import io.github.jls97.boveda.ui.theme.voz
import kotlinx.coroutines.delay

@Composable
internal fun AutofillApp(
    session: VaultSession,
    request: AutofillRequest,
    onFilled: (Dataset) -> Unit,
    onClose: () -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    when (val current = state) {
        VaultState.NoVault -> MessageScreen(
            title = "Todavía no hay bóveda",
            text = "Abre Contraseñora y crea tu bóveda antes de usar el autorrelleno.",
            onClose = onClose,
        )
        VaultState.Locked -> UnlockScreen(
            viewModel {
                val apariencia = (appContext as BovedaApplication).apariencia
                LockViewModel(session) { apariencia.ajustes.value.personalidad }
            },
            allowRestore = false,
            // Who asked for the fill, shown under the title so a fake "unlock" screen drawn by the
            // requesting app cannot pretend the request came from somewhere else (M-04).
            requestContext = when (request) {
                is AutofillRequest.Fill -> "Para: ${request.target.label}"
                is AutofillRequest.FillOtp -> "Código 2FA para: ${request.target.label}"
                is AutofillRequest.Save -> request.pending?.let { "Guardar para: ${it.target.label}" }
            },
            // With the fingerprint on, the master password is never typed inside the requesting
            // app's task: Bóveda opens in its own task and this screen closes with no result (M-04).
            onUsePasswordInApp = {
                appContext.startActivity(Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onClose()
            },
        )
        is VaultState.Unlocked -> {
            val viewModel = viewModel { AutofillViewModel(session) }
            val context = LocalContext.current
            BackHandler { onClose() }
            when (request) {
                is AutofillRequest.Fill -> PickEntryScreen(
                    title = "Rellenar con Contraseñora",
                    entries = current.data.entries,
                    target = request.target,
                    fillDescription = fillDescription(request),
                    emptyText = "La bóveda está vacía.",
                    viewModel = viewModel,
                    onPick = { entry, rememberChoice ->
                        viewModel.pick(entry, request.target, rememberChoice, onLocked = onClose) { chosen ->
                            val dataset = AutofillResponses.filledDataset(
                                context,
                                request.usernameId,
                                request.passwordId,
                                chosen.username,
                                chosen.password,
                            )
                            if (dataset == null) {
                                viewModel.showError("Esa entrada no tiene usuario ni contraseña para estos campos.")
                            } else {
                                onFilled(dataset)
                            }
                        }
                    },
                    onCancel = onClose,
                )
                is AutofillRequest.FillOtp -> if (current.otpAccess == OtpAccess.LOCKED) {
                    MessageScreen(
                        title = "Códigos 2FA bloqueados",
                        text = "Este teléfono no tiene la llave de huella de tus códigos 2FA (copia restaurada o huellas " +
                            "cambiadas). Abre Contraseñora y recupéralos con tu código de recuperación.",
                        onClose = onClose,
                    )
                } else {
                    PickEntryScreen(
                        title = "Rellenar código 2FA",
                        entries = current.data.entries.filter { it.otp != null },
                        target = request.target,
                        fillDescription = "Se rellenará solo el código 2FA.",
                        emptyText = "No tienes ningún código 2FA guardado. Añádelo en Contraseñora, desde la entrada de la cuenta.",
                        viewModel = viewModel,
                        onPick = { entry, rememberChoice ->
                            viewModel.pick(entry, request.target, rememberChoice, onLocked = onClose) { chosen ->
                                val activity = context.findActivity()
                                val cipher = viewModel.otpCipher()
                                if (activity != null && cipher != null) {
                                    // The destination is the last thing the user reads before authorizing.
                                    BiometricPrompts.authenticate(
                                        activity,
                                        "Código 2FA de «${chosen.title}»",
                                        "Para: ${request.target.label}",
                                        cipher,
                                        negativeLabel = "Cancelar",
                                    ) { authorized, error ->
                                        when {
                                            authorized != null -> viewModel.fillCode(authorized, chosen) { code ->
                                                onFilled(AutofillResponses.filledOtpDataset(context, request.otpId, code))
                                            }
                                            error != null -> viewModel.showError(error)
                                        }
                                    }
                                }
                            }
                        },
                        onCancel = onClose,
                    )
                }
                is AutofillRequest.Save -> {
                    // Read again on every unlock: a lock meanwhile (screen off) wipes the store,
                    // and wiped credentials must not reach the save screen.
                    val pending = remember(request) { PendingSaves.get(request.token) }
                    if (pending == null || pending.wiped) {
                        MessageScreen(
                            title = "Nada que guardar",
                            text = "Los datos que se iban a guardar ya no están disponibles. Vuelve a iniciar sesión en la app.",
                            onClose = onClose,
                        )
                    } else {
                        SaveEntryScreen(current.data.entries, pending, viewModel, onDone = onClose, onCancel = onClose)
                    }
                }
            }
        }
    }
}


/**
 * Lists [entries] with those linked to [target] first; [onPick] fills with the chosen one.
 * [fillDescription] tells the user which fields will receive data, so a hidden password field
 * never gets one without them knowing.
 */
@Composable
private fun PickEntryScreen(
    title: String,
    entries: List<VaultEntry>,
    target: AutofillTarget,
    fillDescription: String,
    emptyText: String,
    viewModel: AutofillViewModel,
    onPick: (entry: VaultEntry, rememberChoice: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Off by default: linking is a deliberate decision, never a side effect of a hurried tap.
    var rememberChoice by remember { mutableStateOf(false) }
    ElegirEntradaContenido(
        title = title,
        entries = entries,
        target = target,
        fillDescription = fillDescription,
        emptyText = emptyText,
        query = query,
        onQueryChange = { query = it },
        rememberChoice = rememberChoice,
        onRememberChoiceChange = { rememberChoice = it },
        busy = viewModel.busy,
        error = viewModel.error,
        onPick = onPick,
        onCancel = onCancel,
    )
}

/**
 * Elegir con qué entrada rellenar, sin estado. De arriba abajo, en el orden en que hay que leerlo:
 * la ventanilla con el destino (y lo que dice mostrar) y qué se va a rellenar; los avisos (dominio
 * internacionalizado y antiphishing), todos a la vista en una sola ficha con un solo sello; la
 * búsqueda, la casilla de vincular y las entradas: vinculadas, sugeridas y todas. Sin entradas,
 * nada que buscar ni avisar: un estado vacío. Mientras se vincula o se abre el código, el mostrador
 * lo dice.
 */
@Composable
internal fun ElegirEntradaContenido(
    title: String,
    entries: List<VaultEntry>,
    target: AutofillTarget,
    fillDescription: String,
    emptyText: String,
    query: String,
    onQueryChange: (String) -> Unit,
    rememberChoice: Boolean,
    onRememberChoiceChange: (Boolean) -> Unit,
    busy: Boolean,
    error: String?,
    onPick: (entry: VaultEntry, rememberChoice: Boolean) -> Unit,
    onCancel: () -> Unit,
    lista: LazyListState = rememberLazyListState(),
) {
    val c = ContrasenoraTheme.colors
    val linkable = target.key != null
    val exact = remember(entries, target) { CredentialMatcher.exactMatches(entries, target) }
    // Same package name as a linked app, another signature: never linkable, never suggested.
    val impersonated = remember(entries, target) { CredentialMatcher.impersonationWarnings(entries, target) }
    val canRemember = linkable && impersonated.isEmpty()
    val suggested = remember(entries, target, impersonated) {
        CredentialMatcher.suggestions(entries, target) - impersonated.toSet()
    }
    val searchResults = remember(entries, query) {
        val needle = query.trim().lowercase()
        entries
            .filter {
                needle.isNotEmpty() &&
                    (it.title.lowercase().contains(needle) || it.username.lowercase().contains(needle) || it.url.lowercase().contains(needle))
            }
            .sortedBy { it.title.lowercase() }
    }
    val others = remember(entries, exact, suggested) {
        (entries - exact.toSet() - suggested.toSet()).sortedBy { it.title.lowercase() }
    }
    // Always shown, and as urgent: an unlinked app or site is the realistic phishing case, and an
    // unencrypted page is a warning even when an entry is linked. With nothing to pick, nothing can
    // be filled, so there is nothing to warn about.
    val avisos = if (entries.isEmpty()) {
        emptyList()
    } else {
        listOfNotNull(avisoIdn(target)) +
            FillWarnings.forFill(target, exact, impersonated).map { avisoDe(it, suplantada = impersonated.isNotEmpty()) }
    }

    fun fill(entry: VaultEntry) = onPick(entry, rememberChoice && canRemember)

    val umbral = with(LocalDensity.current) { 56.dp.toPx() }
    val desplazada by remember(lista, umbral) {
        derivedStateOf { lista.firstVisibleItemIndex > 0 || lista.firstVisibleItemScrollOffset > umbral }
    }
    val margen = margenDeLista()

    Scaffold(
        containerColor = c.bgCanvas,
        contentColor = c.textPrimary,
        topBar = {
            BarraSuperior(
                titulo = title,
                mostrarTitulo = desplazada,
                salida = Salida(onCancel, R.drawable.ic_cerrar, "Cancelar"),
                ocupado = busy,
            )
        },
        // Vincular o abrir el código lleva un momento: las filas se apagan y aquí se dice por qué.
        bottomBar = {
            AnimatedVisibility(
                visible = busy,
                enter = fadeIn(tween(Motion.BASE)) + expandVertically(tween(Motion.BASE, easing = Motion.Standard)),
                exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE, easing = Motion.Standard)),
            ) {
                Mostrador {
                    Trabajando(
                        voz("Preparando el relleno. Aquí nada se hace a lo loco…", "Preparando el relleno…"),
                        Modifier.weight(1f).padding(vertical = Spacing.s2),
                    )
                }
            }
        },
    ) { inner ->
        LazyColumn(
            state = lista,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(inner)
                .imePadding(),
            contentPadding = PaddingValues(
                start = margen,
                end = margen,
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + Spacing.s10,
            ),
        ) {
            item(key = "cabecera") { CabeceraGrande(title) }
            item(key = "destino") { Ventanilla(target, fillDescription) }
            if (avisos.isNotEmpty()) {
                item(key = "avisos") { AvisosDestino(avisos, Modifier.padding(top = Spacing.s3)) }
            }
            error?.let { item(key = "error") { TextoError(it, Modifier.padding(top = Spacing.s3)) } }
            if (entries.isEmpty()) {
                // Desde aquí no se puede crear nada: sin acción.
                item(key = "vacia") {
                    EstadoVacio(
                        titulo = voz("Aquí no hay nada que rellenar… todavía.", "Nada que rellenar"),
                        mensaje = emptyText,
                        modifier = Modifier.padding(top = Spacing.s2),
                    )
                }
                return@LazyColumn
            }
            item(key = "busqueda") {
                CampoBusqueda(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Buscar en la bóveda",
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s6),
                )
            }
            // Solo si se puede vincular: junto a una app suplantada no se puede, y el aviso ya lo dice.
            if (canRemember) {
                item(key = "vincular") {
                    FilaCasilla(
                        "Vincular la entrada que elija a ${target.label}",
                        marcada = rememberChoice,
                        onCambio = onRememberChoiceChange,
                        modifier = Modifier.padding(top = Spacing.s2),
                    )
                }
            }
            if (query.isNotBlank()) {
                seccion("Resultados", searchResults, busy, ::fill)
                if (searchResults.isEmpty()) {
                    item(key = "sin-resultados") {
                        Column {
                            TextoVacio(voz("No encuentro nada con «$query». Y mira que soy cotilla.", "Nada coincide con «$query»."))
                            BotonFantasma(
                                "Borrar búsqueda",
                                { onQueryChange("") },
                                Modifier.desbordar(Spacing.s3).padding(top = Spacing.s2),
                                icono = R.drawable.ic_cerrar,
                            )
                        }
                    }
                }
            } else {
                seccion("Vinculadas a ${target.label}", exact, busy, ::fill)
                seccion("Quizá sea una de estas", suggested, busy, ::fill)
                seccion("Todas", others, busy, ::fill)
            }
        }
    }
}

/**
 * La ventanilla: para quién se rellena, en una ficha de papel. Arriba, «Para la web» o «Para la app»
 * en ciruela y su nombre en letra de códigos; debajo, la dirección que dice mostrar en su propia
 * línea; tras la línea de puntos, qué se va a rellenar. TalkBack lee el destino de una vez: «Para la
 * web, banco.es».
 */
@Composable
private fun Ventanilla(target: AutofillTarget, fillDescription: String) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Ficha(relleno = PaddingValues(0.dp)) {
        Column(Modifier.padding(Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            CabeceraDestino(target, if (target.host != null) "Para la web" else "Para la app")
            ClaimedAddress(target)
        }
        LineaPunteada(Modifier.padding(horizontal = Spacing.s4))
        Text(
            fillDescription,
            style = t.body,
            color = c.textPrimary,
            modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        )
    }
}

/**
 * El destino, igual en Elegir y en Guardar: la llave, el [antetitulo] en ciruela («Para la web»,
 * «Credenciales de la app») y el nombre en letra de códigos, que pasa de renglón entre sus partes.
 */
@Composable
private fun CabeceraDestino(target: AutofillTarget, antetitulo: String) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Icon(
            painterResource(R.drawable.ic_llave),
            contentDescription = null,
            tint = c.textLink,
            modifier = Modifier.padding(top = 2.dp).size(Sizes.iconLg),
        )
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(antetitulo, style = t.label, color = c.textLink)
            Text(
                cortesEnPuntos(target.label),
                style = t.secret.copy(fontWeight = FontWeight.Medium),
                color = c.textPrimary,
                modifier = Modifier.padding(top = Spacing.s0_5).semantics { contentDescription = target.label },
            )
        }
    }
}

/**
 * El nombre con un punto de corte invisible tras cada punto y cada guion: un paquete o una
 * dirección largos (`com.banco.login.mobile`, `banco.es.verificacion-clientes.ru`) pasan de renglón
 * entre sus partes y no a mitad de una. Solo para dibujarlo; TalkBack lee el nombre tal cual.
 */
private fun cortesEnPuntos(nombre: String): String = nombre.replace(".", ".\u200B").replace("-", "-\u200B")

/** Encabezado y filas de una sección de la lista; nada si no tiene entradas. */
private fun LazyListScope.seccion(
    title: String,
    entries: List<VaultEntry>,
    busy: Boolean,
    onPick: (VaultEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "titulo/$title") {
        Text(
            title,
            style = ContrasenoraTheme.type.label.copy(fontWeight = FontWeight.Bold),
            color = ContrasenoraTheme.colors.textLink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = Spacing.s8, bottom = Spacing.s1)
                .semantics { heading() },
        )
    }
    itemsIndexed(entries, key = { _, entry -> "$title/${entry.id}" }) { i, entry ->
        FilaEntrada(entry, enabled = !busy, ultima = i == entries.lastIndex) { onPick(entry) }
    }
}

/**
 * Una entrada que se puede elegir, al estilo del fichero: nombre y usuario y una línea de puntos
 * hasta la siguiente. Sin flecha: pulsarla no abre nada, rellena y cierra. Se pulsa entera; mientras
 * se vincula, no.
 */
@Composable
private fun FilaEntrada(entry: VaultEntry, enabled: Boolean, ultima: Boolean, onClick: () -> Unit) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .desbordar(Spacing.s2)
                .clip(ContrasenoraShapes.sm)
                .clickable(enabled = enabled, onClickLabel = "Rellenar", role = Role.Button, onClick = onClick)
                .heightIn(min = Sizes.listItemHeight)
                .padding(horizontal = Spacing.s2, vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { "(sin nombre)" },
                    style = t.bodyStrong,
                    color = if (enabled) c.textPrimary else c.textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.username.isNotEmpty()) {
                    Text(
                        entry.username,
                        style = t.small,
                        color = if (enabled) c.textSecondary else c.textDisabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!ultima) LineaPunteada()
    }
}

/** Lo que se dice cuando una lista no tiene nada. */
@Composable
private fun TextoVacio(texto: String) {
    Text(
        texto,
        style = ContrasenoraTheme.type.bodyLarge,
        color = ContrasenoraTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Spacing.s8),
    )
}

@Composable
private fun SaveEntryScreen(
    entries: List<VaultEntry>,
    pending: PendingSave,
    viewModel: AutofillViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val matches = remember(entries, pending) { CredentialMatcher.exactMatches(entries, pending.target) }
    val impersonated = remember(entries, pending) { CredentialMatcher.impersonationWarnings(entries, pending.target) }
    // The user name was typed in the other app: shown (and saved) without invisible characters.
    val typedUsername = remember(pending) { ExternalText.sanitize(pending.username) }
    // Decided once, from the entries as they were when the screen opened, so a successful save
    // doesn't flip the screen into this message before it closes.
    val alreadyStored = remember(pending) { SaveCapture.alreadyStored(matches, typedUsername, pending.password) }
    if (alreadyStored != null) {
        MessageScreen(
            title = "Ya está en Contraseñora",
            text = "«${alreadyStored.title.ifBlank { "(sin nombre)" }}» ya guarda este usuario y esta contraseña " +
                "para ${pending.target.label}. No hay nada que cambiar.",
            onClose = onCancel,
        )
        return
    }
    var title by remember { mutableStateOf(CredentialMatcher.suggestedTitle(pending.target, entries)) }
    var username by remember { mutableStateOf(typedUsername) }
    // "Actualizar" only comes preselected for an exact match of the destination with the same
    // user and, on the web, anchored to this very host, never to a parent domain of it.
    var replaceId by remember { mutableStateOf(SaveCapture.preselect(matches, typedUsername, pending.target.host)?.id) }
    var revealed by remember { mutableStateOf(false) }
    // Whatever was revealed hides again when the app goes to the background (B-39) and, like
    // everywhere else, after a while on screen.
    OnAppBackground { revealed = false }
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(Comportamiento.CONTRASENA_REVELADA_SEGUNDOS * 1_000L)
            revealed = false
        }
    }

    GuardarContenido(
        pending = pending,
        matches = matches,
        impersonated = impersonated,
        title = title,
        onTitleChange = { title = it },
        username = username,
        onUsernameChange = { username = it },
        replaceId = replaceId,
        onReplaceIdChange = { replaceId = it },
        revealed = revealed,
        onRevealedChange = { revealed = it },
        busy = viewModel.busy,
        error = viewModel.error,
        onSave = { viewModel.save(pending, title, username, replaceId, onDone) },
        onCancel = onCancel,
    )
}

/**
 * Guardar lo que se ha tecleado en otra app, sin estado: un resguardo con el destino, el resumen y
 * las contraseñas (capturada y, al actualizar, la actual), ocultas hasta pulsar el ojo; los avisos
 * en una ficha; y un impreso con dónde guardarla (I, si hay dónde elegir) y los datos (II). Abajo,
 * «No guardar» y «Guardar», del mismo alto, o apilados con letra grande.
 */
@Composable
internal fun GuardarContenido(
    pending: PendingSave,
    matches: List<VaultEntry>,
    impersonated: List<VaultEntry>,
    title: String,
    onTitleChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    replaceId: String?,
    onReplaceIdChange: (String?) -> Unit,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    busy: Boolean,
    error: String?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val existing = replaceId?.let { id -> matches.find { it.id == id } }
    Pantalla(
        titulo = "Guardar en Contraseñora",
        salida = Salida(onCancel, R.drawable.ic_cerrar, "Cancelar"),
        ocupado = busy,
        mostrador = {
            Mostrador {
                if (LocalDensity.current.fontScale >= 1.5f) {
                    // Con letra grande, apilados a todo lo ancho y con aire arriba y abajo (los botones de
                    // la base solo tienen relleno a los lados).
                    val alto = with(LocalDensity.current) { ContrasenoraTheme.type.bodyStrong.lineHeight.toDp() } + Spacing.s4
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        BotonPrimario("Guardar", onSave, Modifier.fillMaxWidth().heightIn(min = alto), enabled = !busy)
                        BotonSecundario("No guardar", onCancel, Modifier.fillMaxWidth().heightIn(min = alto), enabled = !busy)
                    }
                } else {
                    Row(Modifier.weight(1f).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        BotonSecundario("No guardar", onCancel, Modifier.weight(1f).fillMaxHeight(), enabled = !busy)
                        BotonPrimario("Guardar", onSave, Modifier.weight(1f).fillMaxHeight(), enabled = !busy)
                    }
                }
            }
        },
    ) {
        Capturado(pending, existing, username, revealed, onRevealedChange)
        val avisos = listOfNotNull(avisoIdn(pending.target)) +
            FillWarnings.forSave(pending.target, impersonated).map { avisoDe(it, suplantada = impersonated.isNotEmpty()) }
        if (avisos.isNotEmpty()) AvisosDestino(avisos, Modifier.padding(top = Spacing.s3))
        if (matches.isNotEmpty()) {
            Apartado("¿Dónde la guardo?", numero = "I")
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                OpcionGuardar("En una entrada nueva", null, replaceId == null) { onReplaceIdChange(null) }
                matches.forEach { entry ->
                    OpcionGuardar(
                        "Actualizar «${entry.title}»",
                        entry.username.ifEmpty { "sin usuario" },
                        replaceId == entry.id,
                    ) { onReplaceIdChange(entry.id) }
                }
            }
        }
        Apartado("Los datos", numero = if (matches.isNotEmpty()) "II" else null)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            AnimatedVisibility(
                visible = replaceId == null,
                enter = fadeIn(tween(Motion.BASE)) + expandVertically(tween(Motion.BASE, easing = Motion.Standard)),
                exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE, easing = Motion.Standard)),
            ) {
                // The service's name often reads like the user: no keyboard learning here either (B-41).
                NoLearningTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = "Nombre",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                )
            }
            NoLearningTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "Usuario o email",
                // Empty while updating keeps the stored user, which is shown here as a hint.
                placeholder = existing?.username?.takeIf { it.isNotEmpty() },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
            )
            error?.let { TextoError(it) }
        }
    }
}

/**
 * Lo capturado, como un resguardo de ventanilla: de dónde viene (igual que el destino al rellenar, y
 * lo que dice mostrar), el resumen de lo que se va a guardar y, bajo la línea de puntos, la
 * contraseña capturada y la actual. Nunca se sobrescribe a ciegas: el ojo enseña las dos para
 * compararlas.
 */
@Composable
private fun Capturado(
    pending: PendingSave,
    existing: VaultEntry?,
    username: String,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Ficha(relleno = PaddingValues(0.dp)) {
        Column(Modifier.padding(Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            CabeceraDestino(
                pending.target,
                if (pending.target.host != null) "Credenciales de la web" else "Credenciales de la app",
            )
            ClaimedAddress(pending.target)
            if (existing != null) {
                Column {
                    Text("Se actualizará «${existing.title}».", style = t.body, color = c.textPrimary)
                    Text(
                        SaveCapture.changeSummary(existing, username, pending.password),
                        style = t.small,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = Spacing.s1),
                    )
                }
            } else {
                Text(
                    "La contraseña (${pending.password.length} caracteres) se guardará cifrada.",
                    style = t.body,
                    color = c.textPrimary,
                )
            }
        }
        LineaPunteada(Modifier.padding(horizontal = Spacing.s4))
        // What is about to be stored is never a blind overwrite: the user can compare both values.
        Row(
            modifier = Modifier.padding(start = Spacing.s4, end = Spacing.s1, top = Spacing.s2, bottom = Spacing.s3),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(top = Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                ValorGuardado("Capturada", pending.password, revealed)
                if (existing != null) ValorGuardado("Actual", existing.password, revealed)
            }
            BotonIcono(
                icono = if (revealed) R.drawable.ic_ojo_tachado else R.drawable.ic_ojo,
                descripcion = if (revealed) "Ocultar" else "Mostrar",
                onClick = { onRevealedChange(!revealed) },
                tamanoIcono = Sizes.iconMd,
            )
        }
    }
}

/**
 * Una contraseña del resguardo: doce puntos mientras está oculta (un hueco, siempre igual; cuánto
 * mide la nueva ya lo dice el resumen de arriba) o, a la vista, en letra de códigos con las cifras
 * en latón. Una vacía se dice con palabras.
 */
@Composable
private fun ValorGuardado(etiqueta: String, password: String, revealed: Boolean) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Text(etiqueta, style = t.label, color = c.textSecondary)
        when {
            password.isEmpty() -> Text("(vacía)", style = t.body, color = c.textTertiary, modifier = Modifier.padding(top = Spacing.s1))
            revealed -> Text(textoSecreto(password), style = t.secret, modifier = Modifier.padding(top = Spacing.s1))
            else -> Text(
                "••••••••••••",
                style = t.secret,
                color = c.textPrimary,
                maxLines = 1,
                modifier = Modifier.padding(top = Spacing.s1).semantics { contentDescription = "Oculta" },
            )
        }
    }
}

/**
 * Una opción de «¿Dónde la guardo?»: papel con su redondel, que se marca y se enmarca en ciruela
 * al elegirla. TalkBack la lee como botón de opción.
 */
@Composable
private fun OpcionGuardar(titulo: String, detalle: String?, elegida: Boolean, onElegir: () -> Unit) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    val borde by animateColorAsState(
        targetValue = if (elegida) c.brandPrimary else c.borderSubtle,
        animationSpec = if (reduced) snap() else tween(Motion.BASE),
        label = "borde de la opción",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContrasenoraShapes.md)
            .background(c.bgSurface)
            .border(if (elegida) Sizes.inputBorder else 1.dp, borde, ContrasenoraShapes.md)
            .selectable(selected = elegida, onClick = onElegir, role = Role.RadioButton)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Redondel(elegida)
        Column(Modifier.weight(1f)) {
            Text(titulo, style = t.bodyStrong, color = c.textPrimary)
            if (detalle != null) Text(detalle, style = t.small, color = c.textSecondary)
        }
    }
}

/**
 * Pantalla de mensaje del autorrelleno: el isotipo, el título en Young Serif, el texto y «Cerrar».
 * Para cuando no hay nada que elegir ni guardar.
 */
@Composable
internal fun MessageScreen(title: String, text: String, onClose: () -> Unit) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val margen = margenLateral()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
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
                title,
                style = t.display2,
                color = c.textPrimary,
                modifier = Modifier.padding(top = Spacing.s6).semantics { heading() },
            )
            Text(text, style = t.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s3))
            BotonPrimario("Cerrar", onClose, Modifier.fillMaxWidth().padding(top = Spacing.s8))
        }
    }
}

/** What a fill request will write, from the ids the system asked for. */
private fun fillDescription(request: AutofillRequest.Fill): String = when {
    request.usernameId != null && request.passwordId != null -> "Se rellenarán usuario y contraseña."
    request.passwordId != null -> "Solo la contraseña."
    else -> "Solo el usuario."
}

/**
 * The address an app claims to show, on a line of its own in monospace and never inside a
 * sentence: whatever it contains (quotes, a reassuring text) can't pass for part of a warning.
 * Never cut short: the part that matters is at the end (`banco.es.estafa.ru`), so it wraps between
 * its labels instead.
 */
@Composable
private fun ClaimedAddress(target: AutofillTarget) {
    val claimed = FillWarnings.claimedAddress(target) ?: return
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Text("Muestra:", style = t.label, color = c.textSecondary)
        Text(
            cortesEnPuntos(claimed),
            style = t.secret,
            color = c.textPrimary,
            modifier = Modifier
                .padding(top = Spacing.s1)
                .fillMaxWidth()
                .semantics { contentDescription = claimed }
                .clip(ContrasenoraShapes.xs)
                .background(c.bgSunken)
                .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
        )
    }
}

/** Un aviso sobre el destino: el sello que le toca, un título corto y el texto entero. */
private class AvisoDestino(val tipo: TipoAviso, val titulo: String, val texto: String)

/**
 * Shown when the domain has non-ASCII characters: a look-alike of a real domain can hide there.
 * First of the warnings, as urgent.
 */
private fun avisoIdn(target: AutofillTarget): AvisoDestino? =
    if (target.isIdn) {
        AvisoDestino(
            TipoAviso.Peligro,
            "Dominio internacionalizado",
            "Su nombre real tiene caracteres no latinos y se muestra en su forma ASCII («${target.host}»). " +
                "Puede imitar a un dominio conocido: compruébalo con cuidado.",
        )
    } else {
        null
    }

/**
 * Un aviso de [FillWarnings] con un título corto según de qué avisa; el texto, entero y tal cual
 * (sin el «Página sin cifrar:» del principio cuando ese es ya el título). Todo lo que avisa de
 * phishing va como urgente; solo «se guardará sin vincular», que es una consecuencia, va con «Ojo».
 * Lo que no se reconoce, también urgente.
 */
private fun avisoDe(aviso: String, suplantada: Boolean): AvisoDestino {
    val (tipo, titulo) = when {
        aviso == FillWarnings.UNENCRYPTED -> TipoAviso.Peligro to "Página sin cifrar"
        aviso == FillWarnings.BROWSER_WITHOUT_DOMAIN -> TipoAviso.Peligro to "Página sin dirección"
        aviso.startsWith(FillWarnings.NOT_A_BROWSER) -> TipoAviso.Peligro to "No es un navegador"
        aviso.startsWith(FillWarnings.UNUSUAL_ADDRESS) -> TipoAviso.Peligro to "Dirección poco corriente"
        aviso.startsWith(FillWarnings.UNVERIFIED_SIGNATURE) -> TipoAviso.Peligro to "Firma sin verificar"
        aviso == FillWarnings.NO_LINKED_WEB -> TipoAviso.Peligro to "Web sin entrada vinculada"
        aviso == FillWarnings.NO_LINKED_APP -> TipoAviso.Peligro to "App sin entrada vinculada"
        aviso == FillWarnings.CHOOSE_WITH_CARE -> TipoAviso.Peligro to "No se podrá vincular"
        aviso == FillWarnings.SAVED_UNLINKED -> TipoAviso.Aviso to "Se guardará sin vincular"
        suplantada -> TipoAviso.Peligro to "Posible app falsa"
        else -> TipoAviso.Peligro to "Revisa el destino"
    }
    val prefijo = "$titulo: "
    val texto = if (aviso.startsWith(prefijo)) aviso.removePrefix(prefijo).replaceFirstChar { it.uppercase() } else aviso
    return AvisoDestino(tipo, titulo, texto)
}

/**
 * Los avisos del destino en una sola ficha, en su orden y todos a la vista: un solo sello (el más
 * grave) y, por aviso, su título corto y el texto entero en letra normal, separados por líneas de
 * puntos. Con alguno urgente, borde del color del sello y TalkBack lo anuncia en cuanto aparece.
 */
@Composable
private fun AvisosDestino(avisos: List<AvisoDestino>, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val tipo = if (avisos.any { it.tipo == TipoAviso.Peligro }) TipoAviso.Peligro else avisos.first().tipo
    val critico = tipo == TipoAviso.Peligro
    val tinta = tintaDe(tipo)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = if (critico) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        shape = ContrasenoraShapes.md,
        color = c.bgSurface,
        contentColor = c.textPrimary,
        border = BorderStroke(if (critico) Sizes.inputBorder else 1.dp, if (critico) tinta else c.borderSubtle),
    ) {
        Column(Modifier.padding(start = Spacing.s4, end = Spacing.s3, top = Spacing.s4, bottom = Spacing.s4)) {
            avisos.forEachIndexed { i, aviso ->
                if (i > 0) LineaPunteada(Modifier.padding(top = Spacing.s4, bottom = Spacing.s3, end = Spacing.s1))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    // El sello se lee primero: «Urgente. Página sin cifrar.»
                    Text(
                        aviso.titulo,
                        style = t.bodyStrong,
                        color = c.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = if (i == 0) "${tipo.palabra}. ${aviso.titulo}" else aviso.titulo },
                    )
                    if (i == 0) Sello(tipo.palabra, tinta, Modifier.clearAndSetSemantics { })
                }
                Text(aviso.texto, style = t.body, color = c.textPrimary, modifier = Modifier.padding(top = Spacing.s1, end = Spacing.s1))
            }
        }
    }
}
