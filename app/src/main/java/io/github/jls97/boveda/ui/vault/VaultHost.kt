package io.github.jls97.boveda.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jls97.boveda.data.backupReminder
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
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

    /** Vuelve atrás solo si [route] sigue arriba: una pantalla que ya se está yendo no repite el gesto. */
    fun backFrom(route: Route) {
        if (viewModel.backStack.lastOrNull() == route) viewModel.back()
    }

    AnimatedContent(
        targetState = viewModel.backStack.last(),
        transitionSpec = { transicionDeFicha(adelante, reduced) },
        label = "pantallas de la bóveda",
    ) { route ->
        when (route) {
            Route.EntryList -> EntryListScreen(
                entries = entries,
                // Con la bóveda vacía no hay nada que copiar todavía.
                backupReminder = if (entries.isEmpty()) null else backupReminder(backupStatus, System.currentTimeMillis()),
                query = viewModel.query,
                onQueryChange = viewModel::updateQuery,
                onOpen = { viewModel.navigate(Route.Detail(it.id)) },
                onCopyPassword = { viewModel.copy("Contraseña", it.password) },
                onAdd = viewModel::newEntry,
                onGenerator = { viewModel.openGenerator(forEditor = false) },
                onSettings = { viewModel.navigate(Route.Settings) },
                onLock = viewModel::lock,
                snackbar = snackbar,
                restoreUndo = { RestoreUndoBanner(viewModel, onRestoreUndone) },
            )

            is Route.Detail -> {
                val entry = entries.find { it.id == route.entryId }
                if (entry == null) {
                    LaunchedEffect(route) { backFrom(route) }
                } else {
                    EntryDetailScreen(
                        entry = entry,
                        busy = viewModel.busy,
                        onBack = { viewModel.back() },
                        onEdit = { viewModel.editEntry(entry) },
                        onDelete = { viewModel.deleteEntry(entry.id) },
                        onCopy = viewModel::copy,
                        snackbar = snackbar,
                        otpSection = {
                            OtpCard(
                                entry = entry,
                                otpAccess = state.otpAccess,
                                otp = otp,
                                onAdd = {
                                    otp.startAdd(entry.id)
                                    viewModel.navigate(Route.OtpAdd(entry.id))
                                },
                                onRecover = { viewModel.navigate(Route.OtpRecover) },
                            )
                        },
                    )
                }
            }

            // Atrás desde el editor o el generador olvida el borrador o la contraseña generada (I-37, en back()).
            is Route.Edit -> EntryEditScreen(
                draft = viewModel.draft,
                isNew = route.entryId == null,
                busy = viewModel.busy,
                onDraftChange = viewModel::updateDraft,
                onGenerate = { viewModel.openGenerator(forEditor = true) },
                onSave = viewModel::saveDraft,
                onBack = { viewModel.back() },
                snackbar = snackbar,
            )

            is Route.Generator -> GeneratorScreen(
                password = viewModel.generated,
                options = viewModel.generatorOptions,
                forEditor = route.forEditor,
                onOptionsChange = viewModel::updateGeneratorOptions,
                onRegenerate = viewModel::regenerate,
                onCopy = { viewModel.copy("Contraseña", viewModel.generated) },
                onUse = viewModel::useGeneratedPassword,
                onBack = { viewModel.back() },
                snackbar = snackbar,
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
                onRecoverOtp = { viewModel.navigate(Route.OtpRecover) },
                onNewRecoveryCode = {
                    otp.beginRecoveryCode()
                    viewModel.navigate(Route.OtpRecoveryCode(RecoveryCodePurpose.REPLACE))
                },
                onPickExportDestination = onPickExportDestination,
                onRestoreUndone = onRestoreUndone,
                viewModel = viewModel,
                snackbar = snackbar,
            )

            is Route.OtpAdd -> {
                val entry = entries.find { it.id == route.entryId }
                if (entry == null) {
                    LaunchedEffect(route) { backFrom(route) }
                } else {
                    OtpAddScreen(
                        entry = entry,
                        otpAccess = state.otpAccess,
                        otp = otp,
                        onScan = { viewModel.navigate(Route.OtpScan) },
                        onNeedsSetup = { viewModel.navigate(Route.OtpRecoveryCode(RecoveryCodePurpose.SETUP)) },
                        onNeedsRecovery = { viewModel.navigate(Route.OtpRecover) },
                        onSaved = { viewModel.back() },
                        onBack = { viewModel.back() },
                        snackbar = snackbar,
                    )
                }
            }

            Route.OtpScan -> OtpScanScreen(
                onScanned = { text ->
                    otp.updateInput(text)
                    viewModel.back()
                },
                onBack = { viewModel.back() },
            )

            is Route.OtpRecoveryCode -> RecoveryCodeScreen(
                purpose = route.purpose,
                otp = otp,
                onDone = {
                    if (route.purpose == RecoveryCodePurpose.SETUP) {
                        // Back to the entry, past the "add" screen.
                        viewModel.popTo { it is Route.Detail }
                        viewModel.suggestBackup("Has guardado tu primer código 2FA y su secreto solo existe en esta bóveda.")
                    } else {
                        viewModel.back()
                        viewModel.suggestBackup("Has cambiado el código de recuperación 2FA: las copias anteriores necesitan el antiguo.")
                    }
                },
                onBack = { viewModel.back() },
                snackbar = snackbar,
            )

            Route.OtpRecover -> OtpRecoverScreen(
                otp = otp,
                onDone = { viewModel.back() },
                onBack = { viewModel.back() },
                snackbar = snackbar,
            )
        }
    }
}

/**
 * Fichas apiladas: la pantalla nueva se posa encima subiendo un poco mientras la de debajo se
 * aleja; al volver, la de arriba se retira hacia abajo y la de debajo vuelve a su sitio.
 */
private fun AnimatedContentTransitionScope<Route>.transicionDeFicha(adelante: Boolean, reduced: Boolean): ContentTransform {
    if (reduced) return EnterTransition.None togetherWith ExitTransition.None
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
    // Al volver, la ficha que se retira queda encima de la que reaparece.
    if (!adelante) transicion.targetContentZIndex = -1f
    return transicion using SizeTransform(clip = false)
}
