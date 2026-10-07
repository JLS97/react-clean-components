package io.github.jls97.boveda.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jls97.boveda.BovedaApplication
import io.github.jls97.boveda.data.BackupLog
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import io.github.jls97.boveda.ui.components.CreateLocalDocument
import io.github.jls97.boveda.ui.components.TouchOnTyping
import io.github.jls97.boveda.ui.lock.LockViewModel
import io.github.jls97.boveda.ui.lock.SetupScreen
import io.github.jls97.boveda.ui.lock.UnlockScreen
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Personalidad
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import io.github.jls97.boveda.ui.vault.VaultHost
import io.github.jls97.boveda.ui.vault.VaultViewModel

@Composable
fun BovedaApp(session: VaultSession) {
    val context = LocalContext.current
    val state by session.state.collectAsStateWithLifecycle()
    val apariencia = remember(context) { (context.applicationContext as BovedaApplication).apariencia }
    // Los ViewModels leen la personalidad en cada mensaje, así que cambiarla en Ajustes vale al momento.
    val personalidad: () -> Personalidad = { apariencia.ajustes.value.personalidad }
    // El ViewModel de la bóveda y el selector de destino de la copia viven aquí, fuera de las pantallas
    // desbloqueadas: si la bóveda se bloquea con el selector abierto, el resultado llega igualmente y el
    // archivo vacío que el sistema ya había creado se puede borrar (M-10).
    val vaultViewModel = viewModel {
        val app = context.applicationContext
        // Una bóveda anterior al registro de copias no cuenta como «nunca copiada» (R03-7).
        VaultViewModel(
            session,
            app.contentResolver,
            BackupLog(app, vaultExists = session.state.value !is VaultState.NoVault),
            personalidad,
        )
    }
    val exportLauncher = rememberLauncherForActivityResult(CreateLocalDocument("application/octet-stream")) { uri ->
        vaultViewModel.finishExport(uri)
    }
    // También en la raíz: deshacer una restauración desde la bóveda abierta la bloquea, y el aviso
    // de que la anterior ha vuelto lo muestra la pantalla de bloqueo (B-31).
    val lockViewModel = viewModel { LockViewModel(session, personalidad) }
    val reduced = rememberReducedMotion()
    // Escribir con el teclado en pantalla también pospone el autobloqueo (I-31).
    TouchOnTyping(onTyping = session::touch) {
        AnimatedContent(
            targetState = state,
            // Solo cambia de pantalla al cambiar de estado; los datos de la bóveda abierta se
            // actualizan dentro de la misma.
            contentKey = { it::class },
            transitionSpec = { transicionDeEstado(initialState, targetState, reduced) },
            label = "estado de la bóveda",
        ) { current ->
            when (current) {
                VaultState.NoVault -> SetupScreen(lockViewModel)
                VaultState.Locked -> UnlockScreen(
                    lockViewModel,
                    // Se está yendo porque se ha abierto: el arco del candado sube (momento de marca).
                    abriendo = transition.targetState == EnterExitState.PostExit && state is VaultState.Unlocked,
                )
                is VaultState.Unlocked -> VaultHost(
                    session = session,
                    state = current,
                    viewModel = vaultViewModel,
                    personalidad = personalidad,
                    onPickExportDestination = { fileName -> exportLauncher.launch(fileName) },
                    onRestoreUndone = { lockViewModel.showNotice(LockViewModel.RESTORE_UNDONE) },
                )
            }
        }
    }
}

/**
 * Cómo se pasa de un estado a otro. Al abrir la bóveda, la ventanilla se abre: la pantalla de
 * bloqueo aguanta lo justo para que el arco suba y se desvanece creciendo un poco, mientras la
 * bóveda sube desde abajo. Al bloquear, todo desaparece enseguida: nada de lo que había abierto se
 * queda a la vista mientras dura la animación.
 */
private fun AnimatedContentTransitionScope<VaultState>.transicionDeEstado(
    desde: VaultState,
    hacia: VaultState,
    reduced: Boolean,
): ContentTransform {
    if (reduced) return EnterTransition.None togetherWith ExitTransition.None
    val abre = desde !is VaultState.Unlocked && hacia is VaultState.Unlocked
    val cierra = desde is VaultState.Unlocked
    return when {
        abre -> (
            fadeIn(tween(Motion.SLOW, delayMillis = 200, easing = Motion.Emphasized)) +
                slideInVertically(tween(Motion.SLOW + 80, delayMillis = 200, easing = Motion.Emphasized)) { it / 12 }
            ) togetherWith (
            fadeOut(tween(Motion.BASE, delayMillis = 200, easing = Motion.Exit)) +
                scaleOut(tween(Motion.SLOW, delayMillis = 140, easing = Motion.Exit), targetScale = 1.04f)
            )
        cierra -> fadeIn(tween(Motion.BASE)) togetherWith fadeOut(tween(Motion.INSTANT))
        else -> fadeIn(tween(Motion.BASE, delayMillis = 60)) togetherWith fadeOut(tween(Motion.FAST))
    } using SizeTransform(clip = false)
}
