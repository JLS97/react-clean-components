package io.github.jls97.boveda.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jls97.boveda.data.BackupLog
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import io.github.jls97.boveda.ui.components.CreateLocalDocument
import io.github.jls97.boveda.ui.components.TouchOnTyping
import io.github.jls97.boveda.ui.lock.LockViewModel
import io.github.jls97.boveda.ui.lock.SetupScreen
import io.github.jls97.boveda.ui.lock.UnlockScreen
import io.github.jls97.boveda.ui.vault.VaultHost
import io.github.jls97.boveda.ui.vault.VaultViewModel

@Composable
fun BovedaApp(session: VaultSession) {
    val context = LocalContext.current
    val state by session.state.collectAsStateWithLifecycle()
    // El ViewModel de la bóveda y el selector de destino de la copia viven aquí, fuera de las pantallas
    // desbloqueadas: si la bóveda se bloquea con el selector abierto, el resultado llega igualmente y el
    // archivo vacío que el sistema ya había creado se puede borrar (M-10).
    val vaultViewModel = viewModel {
        val app = context.applicationContext
        VaultViewModel(session, app.contentResolver, BackupLog(app))
    }
    val exportLauncher = rememberLauncherForActivityResult(CreateLocalDocument("application/octet-stream")) { uri ->
        vaultViewModel.finishExport(uri)
    }
    // Escribir con el teclado en pantalla también pospone el autobloqueo (I-31).
    TouchOnTyping(onTyping = session::touch) {
        when (val current = state) {
            VaultState.NoVault -> SetupScreen(viewModel { LockViewModel(session) })
            VaultState.Locked -> UnlockScreen(viewModel { LockViewModel(session) })
            is VaultState.Unlocked -> VaultHost(
                session = session,
                state = current,
                viewModel = vaultViewModel,
                onPickExportDestination = { fileName -> exportLauncher.launch(fileName) },
            )
        }
    }
}
