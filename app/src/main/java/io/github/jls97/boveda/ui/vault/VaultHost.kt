package io.github.jls97.boveda.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState

/** Screens shown while the vault is unlocked, with a simple in-memory back stack. */
@Composable
fun VaultHost(session: VaultSession, state: VaultState.Unlocked) {
    val context = LocalContext.current
    val viewModel = viewModel { VaultViewModel(session, context.applicationContext.contentResolver) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = viewModel.backStack.size > 1) { viewModel.back() }

    val entries = state.data.entries
    when (val route = viewModel.backStack.last()) {
        Route.EntryList -> EntryListScreen(
            entries = entries,
            query = viewModel.query,
            onQueryChange = { viewModel.query = it },
            onOpen = { viewModel.navigate(Route.Detail(it.id)) },
            onAdd = viewModel::newEntry,
            onGenerator = { viewModel.openGenerator(forEditor = false) },
            onSettings = { viewModel.navigate(Route.Settings) },
            onLock = viewModel::lock,
            snackbar = snackbar,
        )

        is Route.Detail -> {
            val entry = entries.find { it.id == route.entryId }
            if (entry == null) {
                LaunchedEffect(route) { viewModel.back() }
            } else {
                EntryDetailScreen(
                    entry = entry,
                    busy = viewModel.busy,
                    onBack = { viewModel.back() },
                    onEdit = { viewModel.editEntry(entry) },
                    onDelete = { viewModel.deleteEntry(entry.id) },
                    onCopy = viewModel::copy,
                    snackbar = snackbar,
                )
            }
        }

        is Route.Edit -> EntryEditScreen(
            draft = viewModel.draft,
            isNew = route.entryId == null,
            busy = viewModel.busy,
            onDraftChange = { viewModel.draft = it },
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
            entryCount = entries.size,
            viewModel = viewModel,
            snackbar = snackbar,
        )
    }
}
