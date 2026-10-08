package io.github.jls97.boveda.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jls97.boveda.data.backupReminder
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import io.github.jls97.boveda.ui.components.SinToquesSiSeVa
import io.github.jls97.boveda.ui.otp.OtpAddScreen
import io.github.jls97.boveda.ui.otp.OtpCard
import io.github.jls97.boveda.ui.otp.OtpRecoverScreen
import io.github.jls97.boveda.ui.otp.OtpScanScreen
import io.github.jls97.boveda.ui.otp.OtpViewModel
import io.github.jls97.boveda.ui.otp.RecoveryCodePurpose
import io.github.jls97.boveda.ui.otp.RecoveryCodeScreen
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Personalidad
import io.github.jls97.boveda.ui.theme.rememberReducedMotion

/**
 * Screens shown while the vault is unlocked, with a simple in-memory back stack. [viewModel] and
 * [onPickExportDestination] come from the root, where they outlive a lock (M-10).
 */
@Composable
fun VaultHost(
    session: VaultSession,
    state: VaultState.Unlocked,
    viewModel: VaultViewModel,
    /** Registro de voz elegido en Ajustes, para los mensajes de los ViewModels. */
    personalidad: () -> Personalidad,
    onPickExportDestination: (String) -> Unit,
    /** Tras deshacer una restauración la bóveda queda bloqueada: avisa a la pantalla de bloqueo. */
    onRestoreUndone: () -> Unit,
) {
    val otp = viewModel { OtpViewModel(session, personalidad) }
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(otp) {
        otp.messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = viewModel.backStack.size > 1) { viewModel.back() }

    val entries = state.data.entries
    val reduced = rememberReducedMotion()
    // Hacia dónde se mueve la pila: una ficha nueva se pone encima; al volver, se retira.
    val profundidad = viewModel.backStack.size
    var profundidadAnterior by remember { mutableIntStateOf(profundidad) }
    val adelante = profundidad >= profundidadAnterior
    SideEffect { profundidadAnterior = profundidad }

    // El fichero recuerda dónde estaba al volver de una ficha, y sus filas solo entran escalonadas
    // la primera vez que se ve tras abrir la bóveda.
    val listaEstado = rememberLazyListState()
    var listaVista by remember { mutableStateOf(false) }

    /** Vuelve atrás solo si [route] sigue arriba: una pantalla que ya se está yendo no repite el gesto. */
    fun backFrom(route: Route) {
        if (viewModel.backStack.lastOrNull() == route) viewModel.back()
    }

    AnimatedContent(
        targetState = viewModel.backStack.last(),
        transitionSpec = { transicionDeFicha(adelante, reduced) },
        label = "pantallas de la bóveda",
    ) { route ->
        // La pantalla que se va sigue a la vista un momento: no atiende toques ni el gesto de atrás,
        // no repite el aviso de abajo y no cambia mientras se aleja aunque el ViewModel ya haya
        // olvidado su borrador o su contraseña generada.
        val activa = transition.targetState == EnterExitState.Visible
        val snackbarAqui = if (activa) snackbar else remember { SnackbarHostState() }
        // La tapa de SinToquesSiSeVa para el dedo, pero no el teclado ni las acciones de TalkBack:
        // lo que haga la pantalla que se va no llega al ViewModel. Se lee al llamar, no al componer.
        val sigueActiva = rememberUpdatedState(activa)

        /** [accion] solo si esta pantalla sigue siendo la de arriba. */
        fun siActiva(accion: () -> Unit): () -> Unit = { if (sigueActiva.value) accion() }
        SinToquesSiSeVa(activa) {
            when (route) {
                Route.EntryList -> {
                    EntryListScreen(
                        entries = entries,
                        // Con la bóveda vacía no hay nada que copiar todavía.
                        backupReminder = if (entries.isEmpty()) null else backupReminder(backupStatus, System.currentTimeMillis()),
                        query = viewModel.query,
                        onQueryChange = viewModel::updateQuery,
                        onOpen = { if (sigueActiva.value) viewModel.navigate(Route.Detail(it.id)) },
                        onCopyPassword = { if (sigueActiva.value) viewModel.copy("Contraseña", it.password) },
                        onAdd = siActiva(viewModel::newEntry),
                        onGenerator = siActiva { viewModel.openGenerator(forEditor = false) },
                        onSettings = siActiva { viewModel.navigate(Route.Settings) },
                        onLock = viewModel::lock,
                        snackbar = snackbarAqui,
                        restoreUndo = { RestoreUndoBanner(viewModel, onRestoreUndone) },
                        lista = listaEstado,
                        animarEntrada = !listaVista,
                    )
                    LaunchedEffect(Unit) { listaVista = true }
                }

                is Route.Detail -> {
                    val entry = quietaAlIrse(entries.find { it.id == route.entryId })
                    if (entry == null) {
                        LaunchedEffect(route) { backFrom(route) }
                    } else {
                        EntryDetailScreen(
                            entry = entry,
                            busy = viewModel.busy,
                            onBack = siActiva { backFrom(route) },
                            onEdit = siActiva { viewModel.editEntry(entry) },
                            onDelete = siActiva { viewModel.deleteEntry(entry.id) },
                            onCopy = { que, valor -> if (sigueActiva.value) viewModel.copy(que, valor) },
                            snackbar = snackbarAqui,
                            otpSection = {
                                OtpCard(
                                    entry = entry,
                                    otpAccess = state.otpAccess,
                                    otp = otp,
                                    onAdd = siActiva {
                                        otp.startAdd(entry.id)
                                        viewModel.navigate(Route.OtpAdd(entry.id))
                                    },
                                    onRecover = siActiva { viewModel.navigate(Route.OtpRecover) },
                                )
                            },
                        )
                    }
                }

                // Atrás desde el editor o el generador olvida el borrador o la contraseña generada (I-37, en back()).
                is Route.Edit -> EntryEditScreen(
                    draft = quietaAlIrse(viewModel.draft),
                    isNew = route.entryId == null,
                    busy = viewModel.busy,
                    // El borrador ya olvidado no vuelve por una tecla tardía en el editor que se va (I-37).
                    onDraftChange = { if (sigueActiva.value) viewModel.updateDraft(it) },
                    onGenerate = siActiva { viewModel.openGenerator(forEditor = true) },
                    onSave = siActiva(viewModel::saveDraft),
                    onBack = siActiva { backFrom(route) },
                    snackbar = snackbarAqui,
                )

                is Route.Generator -> GeneratorScreen(
                    password = quietaAlIrse(viewModel.generated),
                    options = viewModel.generatorOptions,
                    forEditor = route.forEditor,
                    onOptionsChange = { if (sigueActiva.value) viewModel.updateGeneratorOptions(it) },
                    onRegenerate = siActiva(viewModel::regenerate),
                    onCopy = siActiva { viewModel.copy("Contraseña", viewModel.generated) },
                    onUse = siActiva(viewModel::useGeneratedPassword),
                    onBack = siActiva { backFrom(route) },
                    snackbar = snackbarAqui,
                )

                Route.Settings -> SettingsScreen(
                    settings = state.data.settings,
                    biometricEnabled = state.biometricEnabled,
                    deviceKeySecurityLevel = state.deviceKeySecurityLevel,
                    deviceKeyWarning = state.deviceKeyWarning,
                    entryCount = entries.size,
                    otpAccess = state.otpAccess,
                    otpCount = entries.count { it.otp != null },
                    kdfParams = state.kdfParams,
                    kdfUpgradeWarning = state.kdfUpgradeWarning,
                    onRecoverOtp = siActiva { viewModel.navigate(Route.OtpRecover) },
                    onNewRecoveryCode = siActiva {
                        otp.beginRecoveryCode()
                        viewModel.navigate(Route.OtpRecoveryCode(RecoveryCodePurpose.REPLACE))
                    },
                    onPickExportDestination = { if (sigueActiva.value) onPickExportDestination(it) },
                    onRestoreUndone = onRestoreUndone,
                    viewModel = viewModel,
                    snackbar = snackbarAqui,
                )

                is Route.OtpAdd -> {
                    val entry = quietaAlIrse(entries.find { it.id == route.entryId })
                    if (entry == null) {
                        LaunchedEffect(route) { backFrom(route) }
                    } else {
                        OtpAddScreen(
                            entry = entry,
                            otpAccess = state.otpAccess,
                            otp = otp,
                            onScan = siActiva { viewModel.navigate(Route.OtpScan) },
                            onNeedsSetup = siActiva { viewModel.navigate(Route.OtpRecoveryCode(RecoveryCodePurpose.SETUP)) },
                            onNeedsRecovery = siActiva { viewModel.navigate(Route.OtpRecover) },
                            onSaved = siActiva { backFrom(route) },
                            onBack = siActiva { backFrom(route) },
                            snackbar = snackbarAqui,
                        )
                    }
                }

                Route.OtpScan -> OtpScanScreen(
                    // Una segunda lectura mientras el escáner se va no vuelve a sacar otra pantalla de la pila.
                    onScanned = { text ->
                        if (sigueActiva.value) {
                            otp.updateInput(text)
                            backFrom(route)
                        }
                    },
                    onBack = siActiva { backFrom(route) },
                )

                is Route.OtpRecoveryCode -> RecoveryCodeScreen(
                    purpose = route.purpose,
                    otp = otp,
                    onDone = siActiva {
                        if (route.purpose == RecoveryCodePurpose.SETUP) {
                            // Back to the entry, past the "add" screen.
                            viewModel.popTo { it is Route.Detail }
                            viewModel.suggestBackup("Has guardado tu primer código 2FA y su secreto solo existe en esta bóveda.")
                        } else {
                            viewModel.back()
                            viewModel.suggestBackup("Has cambiado el código de recuperación 2FA: las copias anteriores necesitan el antiguo.")
                        }
                    },
                    onBack = siActiva { backFrom(route) },
                    snackbar = snackbarAqui,
                )

                Route.OtpRecover -> OtpRecoverScreen(
                    otp = otp,
                    onDone = siActiva { backFrom(route) },
                    onBack = siActiva { backFrom(route) },
                    snackbar = snackbarAqui,
                )
            }
        }
    }
}

/**
 * [valor] mientras la pantalla está activa; mientras se va, el último que tuvo activa. Así la ficha
 * que se retira no se queda en blanco a media animación cuando el ViewModel ya la ha vaciado.
 */
@Composable
private fun <T> AnimatedVisibilityScope.quietaAlIrse(valor: T): T {
    val ultimo = remember { mutableStateOf(valor) }
    val activa = transition.targetState == EnterExitState.Visible
    if (activa) SideEffect { ultimo.value = valor }
    return if (activa) valor else ultimo.value
}

/**
 * Fichas apiladas: la pantalla nueva se posa encima subiendo un poco mientras la de debajo se
 * aleja; al volver, la de arriba se retira hacia abajo y la de debajo vuelve a su sitio.
 */
private fun AnimatedContentTransitionScope<Route>.transicionDeFicha(adelante: Boolean, reduced: Boolean): ContentTransform {
    if (reduced) return EnterTransition.None togetherWith ExitTransition.None
    // La que llega queda siempre encima, también al volver: es la que recibe los toques.
    val transicion = if (adelante) {
        (
            fadeIn(tween(Motion.BASE, delayMillis = 40, easing = Motion.Emphasized)) +
                slideInVertically(tween(Motion.SLOW, easing = Motion.Emphasized)) { it / 14 }
            ) togetherWith (
            fadeOut(tween(Motion.BASE, easing = Motion.Exit)) +
                scaleOut(tween(Motion.SLOW, easing = Motion.Emphasized), targetScale = 0.96f)
            )
    } else {
        (
            fadeIn(tween(Motion.BASE, easing = Motion.Emphasized)) +
                scaleIn(tween(Motion.SLOW, easing = Motion.Emphasized), initialScale = 0.96f)
            ) togetherWith (
            fadeOut(tween(Motion.BASE, easing = Motion.Exit)) +
                slideOutVertically(tween(Motion.BASE + 60, easing = Motion.Exit)) { it / 14 }
            )
    }
    return transicion using SizeTransform(clip = false)
}
