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
import io.github.jls97.boveda.ui.otp.OtpAddScreen
import io.github.jls97.boveda.ui.otp.OtpCard
import io.github.jls97.boveda.ui.otp.OtpRecoverScreen
import io.github.jls97.boveda.ui.otp.OtpScanScreen
import io.github.jls97.boveda.ui.otp.OtpViewModel
import io.github.jls97.boveda.ui.otp.RecoveryCodePurpose
import io.github.jls97.boveda.ui.otp.RecoveryCodeScreen

/** Screens shown while the vault is unlocked, with a simple in-memory back stack. */
@Composable
fun VaultHost(session: VaultSession, state: VaultState.Unlocked) {
    val context = LocalContext.current
    val viewModel = viewModel { VaultViewModel(session, context.applicationContext.contentResolver) }
    val otp = viewModel { OtpViewModel(session) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(otp) {
        otp.messages.collect { snackbar.showSnackbar(it) }
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
            otpAccess = state.otpAccess,
            otpCount = entries.count { it.otp != null },
            onRecoverOtp = { viewModel.navigate(Route.OtpRecover) },
            onNewRecoveryCode = {
                otp.beginRecoveryCode()
                viewModel.navigate(Route.OtpRecoveryCode(RecoveryCodePurpose.REPLACE))
            },
            viewModel = viewModel,
            snackbar = snackbar,
        )

        is Route.OtpAdd -> {
            val entry = entries.find { it.id == route.entryId }
            if (entry == null) {
                LaunchedEffect(route) { viewModel.back() }
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
                } else {
                    viewModel.back()
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
