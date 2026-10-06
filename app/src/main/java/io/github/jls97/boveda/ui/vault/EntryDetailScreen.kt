package io.github.jls97.boveda.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.ui.components.BackButton
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.OnAppBackground
import io.github.jls97.boveda.ui.components.autofillTargetLabel
import io.github.jls97.boveda.ui.components.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entry: VaultEntry,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    snackbar: SnackbarHostState,
    otpSection: @Composable () -> Unit = {},
) {
    var revealPassword by remember(entry.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // En segundo plano la contraseña vuelve a ocultarse y no reaparece en claro al volver (B-39).
    OnAppBackground { revealPassword = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onEdit, enabled = !busy) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = ScreenPadding,
        ) {
            if (entry.username.isNotEmpty()) {
                DetailCard(label = "Usuario o email", value = entry.username) {
                    TextButton(onClick = { onCopy("Usuario", entry.username) }) { Text("Copiar") }
                }
            }
            if (entry.password.isNotEmpty()) {
                DetailCard(
                    label = "Contraseña",
                    value = if (revealPassword) entry.password else "•".repeat(12),
                    monospace = revealPassword,
                ) {
                    TextButton(onClick = { revealPassword = !revealPassword }) {
                        Text(if (revealPassword) "Ocultar" else "Mostrar")
                    }
                    TextButton(onClick = { onCopy("Contraseña", entry.password) }) { Text("Copiar") }
                }
            }
            otpSection()
            if (entry.url.isNotEmpty()) {
                DetailCard(label = "Web o app", value = entry.url) {
                    TextButton(onClick = { onCopy("Dirección", entry.url) }) { Text("Copiar") }
                }
            }
            if (entry.notes.isNotEmpty()) {
                DetailCard(label = "Notas", value = entry.notes)
            }
            if (entry.autofillTargets.isNotEmpty()) {
                DetailCard(
                    label = "Autorrelleno vinculado a",
                    value = entry.autofillTargets.joinToString("\n") { autofillTargetLabel(it) },
                )
            }
            Text(
                "Creada: ${formatDate(entry.createdAt)}\nModificada: ${formatDate(entry.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar entrada?",
            text = "Se borrará «${entry.title}» de la bóveda" +
                (if (entry.otp != null) ", con su código 2FA" else "") + ". No se puede deshacer.",
            confirmLabel = "Eliminar",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Card with a label, a value and optional actions. The value can't be selected, so it can
 * only reach the clipboard through "Copiar", which marks it as sensitive and clears it later. */
@Composable
private fun DetailCard(
    label: String,
    value: String,
    monospace: Boolean = false,
    actions: (@Composable () -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
            )
            if (actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }
        }
    }
}
