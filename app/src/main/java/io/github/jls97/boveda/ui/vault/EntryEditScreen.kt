package io.github.jls97.boveda.ui.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.ui.components.BackButton
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.StrengthMeter
import io.github.jls97.boveda.ui.components.autofillTargetLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    draft: EntryDraft,
    isNew: Boolean,
    busy: Boolean,
    onDraftChange: (EntryDraft) -> Unit,
    onGenerate: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nueva entrada" else "Editar entrada") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(onClick = onSave, enabled = !busy) { Text("Guardar") }
                },
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
            verticalArrangement = ScreenPadding,
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onDraftChange(draft.copy(title = it)) },
                label = { Text("Nombre (p. ej. Banco, Gmail)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.username,
                onValueChange = { onDraftChange(draft.copy(username = it)) },
                label = { Text("Usuario o email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = draft.password,
                onValueChange = { onDraftChange(draft.copy(password = it)) },
                label = "Contraseña",
            )
            StrengthMeter(draft.password)
            OutlinedButton(onClick = onGenerate, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Generar una contraseña segura")
            }
            OutlinedTextField(
                value = draft.url,
                onValueChange = { onDraftChange(draft.copy(url = it)) },
                label = { Text("Web o app (p. ej. banco.es)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { onDraftChange(draft.copy(notes = it)) },
                label = { Text("Notas") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.autofillTargets.isNotEmpty()) {
                Text("Autorrelleno vinculado a", style = MaterialTheme.typography.titleSmall)
                draft.autofillTargets.forEach { target ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(autofillTargetLabel(target), modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDraftChange(draft.copy(autofillTargets = draft.autofillTargets - target)) }) {
                            Text("Quitar")
                        }
                    }
                }
            }
        }
    }
}
