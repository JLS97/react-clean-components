package io.github.jls97.boveda.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import io.github.jls97.boveda.ui.lock.LockViewModel
import io.github.jls97.boveda.ui.lock.SetupScreen
import io.github.jls97.boveda.ui.lock.UnlockScreen
import io.github.jls97.boveda.ui.vault.VaultHost

@Composable
fun BovedaApp(session: VaultSession) {
    val state by session.state.collectAsStateWithLifecycle()
    when (val current = state) {
        VaultState.NoVault -> SetupScreen(viewModel { LockViewModel(session) })
        VaultState.Locked -> UnlockScreen(viewModel { LockViewModel(session) })
        is VaultState.Unlocked -> VaultHost(session, current)
    }
}
