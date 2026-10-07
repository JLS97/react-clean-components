package io.github.jls97.boveda.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.core.vault.VaultEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    entries: List<VaultEntry>,
    backupReminder: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (VaultEntry) -> Unit,
    onAdd: () -> Unit,
    onGenerator: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    snackbar: SnackbarHostState,
    /** Aviso con las acciones sobre la última restauración, mientras se pueda deshacer (B-31). */
    restoreUndo: @Composable () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val visible = remember(entries, query) {
        val needle = query.trim().lowercase()
        entries
            .filter {
                needle.isEmpty() ||
                    it.title.lowercase().contains(needle) ||
                    it.username.lowercase().contains(needle) ||
                    it.url.lowercase().contains(needle)
            }
            .sortedBy { it.title.lowercase() }
    }

    Scaffold(
        modifier = Modifier.testTag("entry_list"),
        topBar = {
            TopAppBar(
                title = { Text("Bóveda") },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Filled.Lock, contentDescription = "Bloquear ahora")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Generador de contraseñas") },
                                onClick = {
                                    menuOpen = false
                                    onGenerator()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Ajustes y copias") },
                                onClick = {
                                    menuOpen = false
                                    onSettings()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva entrada")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            // Aviso discreto de copia de seguridad (B-38): tocarlo lleva a Ajustes y copias.
            if (backupReminder != null) {
                Text(
                    text = "$backupReminder Toca para ir a las copias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .clickable { onSettings() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            restoreUndo()
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (entries.isEmpty()) {
                            "Tu bóveda está vacía.\nPulsa + para guardar tu primera contraseña."
                        } else {
                            "Nada coincide con «$query»."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(entry.title.ifBlank { "(sin nombre)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = if (entry.username.isNotEmpty()) {
                                { Text(entry.username, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            } else {
                                null
                            },
                            leadingContent = { InitialAvatar(entry.title) },
                            trailingContent = if (entry.otp != null) {
                                { Text("2FA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                            modifier = Modifier.clickable { onOpen(entry) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun InitialAvatar(title: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

internal val ScreenPadding = Arrangement.spacedBy(12.dp)
