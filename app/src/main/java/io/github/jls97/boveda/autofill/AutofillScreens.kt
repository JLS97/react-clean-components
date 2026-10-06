package io.github.jls97.boveda.autofill

import android.service.autofill.Dataset
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jls97.boveda.core.autofill.AutofillTarget
import io.github.jls97.boveda.core.autofill.CredentialMatcher
import io.github.jls97.boveda.core.autofill.ExternalText
import io.github.jls97.boveda.core.autofill.SaveCapture
import io.github.jls97.boveda.core.autofill.TrustedBrowsers
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.session.OtpAccess
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.session.VaultState
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.findActivity
import io.github.jls97.boveda.ui.lock.LockViewModel
import io.github.jls97.boveda.ui.lock.UnlockScreen

@Composable
internal fun AutofillApp(
    session: VaultSession,
    request: AutofillRequest,
    onFilled: (Dataset) -> Unit,
    onClose: () -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    when (val current = state) {
        VaultState.NoVault -> MessageScreen(
            title = "Todavía no hay bóveda",
            text = "Abre Bóveda y crea tu bóveda antes de usar el autorrelleno.",
            onClose = onClose,
        )
        VaultState.Locked -> UnlockScreen(viewModel { LockViewModel(session) }, allowRestore = false)
        is VaultState.Unlocked -> {
            val viewModel = viewModel { AutofillViewModel(session) }
            val context = LocalContext.current
            BackHandler { onClose() }
            when (request) {
                is AutofillRequest.Fill -> PickEntryScreen(
                    title = "Rellenar con Bóveda",
                    entries = current.data.entries,
                    target = request.target,
                    fillDescription = fillDescription(request),
                    emptyText = "La bóveda está vacía.",
                    viewModel = viewModel,
                    onPick = { entry, rememberChoice ->
                        viewModel.pick(entry, request.target, rememberChoice, onLocked = onClose) { chosen ->
                            val dataset = AutofillResponses.filledDataset(
                                context,
                                request.usernameId,
                                request.passwordId,
                                chosen.username,
                                chosen.password,
                            )
                            if (dataset == null) {
                                viewModel.showError("Esa entrada no tiene usuario ni contraseña para estos campos.")
                            } else {
                                onFilled(dataset)
                            }
                        }
                    },
                    onCancel = onClose,
                )
                is AutofillRequest.FillOtp -> if (current.otpAccess == OtpAccess.LOCKED) {
                    MessageScreen(
                        title = "Códigos 2FA bloqueados",
                        text = "Este móvil no tiene la llave de huella de tus códigos 2FA (copia restaurada o huellas " +
                            "cambiadas). Abre Bóveda y recupéralos con tu código de recuperación.",
                        onClose = onClose,
                    )
                } else {
                    PickEntryScreen(
                        title = "Rellenar código 2FA",
                        entries = current.data.entries.filter { it.otp != null },
                        target = request.target,
                        fillDescription = "Se rellenará solo el código 2FA.",
                        emptyText = "No tienes ningún código 2FA guardado. Añádelo en Bóveda, desde la entrada de la cuenta.",
                        viewModel = viewModel,
                        onPick = { entry, rememberChoice ->
                            viewModel.pick(entry, request.target, rememberChoice, onLocked = onClose) { chosen ->
                                val activity = context.findActivity()
                                val cipher = viewModel.otpCipher()
                                if (activity != null && cipher != null) {
                                    // The destination is the last thing the user reads before authorizing.
                                    BiometricPrompts.authenticate(
                                        activity,
                                        "Código 2FA de «${chosen.title}»",
                                        "Para: ${request.target.label}",
                                        cipher,
                                        negativeLabel = "Cancelar",
                                    ) { authorized, error ->
                                        when {
                                            authorized != null -> viewModel.fillCode(authorized, chosen) { code ->
                                                onFilled(AutofillResponses.filledOtpDataset(context, request.otpId, code))
                                            }
                                            error != null -> viewModel.showError(error)
                                        }
                                    }
                                }
                            }
                        },
                        onCancel = onClose,
                    )
                }
                is AutofillRequest.Save -> {
                    val pending = request.pending
                    if (pending == null) {
                        MessageScreen(
                            title = "Nada que guardar",
                            text = "Los datos que se iban a guardar ya no están disponibles. Vuelve a iniciar sesión en la app.",
                            onClose = onClose,
                        )
                    } else {
                        SaveEntryScreen(current.data.entries, pending, viewModel, onDone = onClose, onCancel = onClose)
                    }
                }
            }
        }
    }
}

/**
 * Lists [entries] with those linked to [target] first; [onPick] fills with the chosen one.
 * [fillDescription] tells the user which fields will receive data, so a hidden password field
 * never gets one without them knowing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickEntryScreen(
    title: String,
    entries: List<VaultEntry>,
    target: AutofillTarget,
    fillDescription: String,
    emptyText: String,
    viewModel: AutofillViewModel,
    onPick: (entry: VaultEntry, rememberChoice: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Off by default: linking is a deliberate decision, never a side effect of a hurried tap.
    var rememberChoice by remember { mutableStateOf(false) }
    val linkable = target.key != null
    val exact = remember(entries, target) { CredentialMatcher.exactMatches(entries, target) }
    // Same package name as a linked app, another signature: never linkable, never suggested.
    val impersonated = remember(entries, target) { CredentialMatcher.impersonationWarnings(entries, target) }
    val canRemember = linkable && impersonated.isEmpty()
    val suggested = remember(entries, target, impersonated) {
        CredentialMatcher.suggestions(entries, target) - impersonated.toSet()
    }
    val searchResults = remember(entries, query) {
        val needle = query.trim().lowercase()
        entries
            .filter {
                needle.isNotEmpty() &&
                    (it.title.lowercase().contains(needle) || it.username.lowercase().contains(needle) || it.url.lowercase().contains(needle))
            }
            .sortedBy { it.title.lowercase() }
    }
    val others = remember(entries, exact, suggested) {
        (entries - exact.toSet() - suggested.toSet()).sortedBy { it.title.lowercase() }
    }

    fun fill(entry: VaultEntry) = onPick(entry, rememberChoice && canRemember)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancelar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize(),
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (target.host != null) "Web: ${target.label}" else "App: ${target.label}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    idnWarning(target)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    Text(fillDescription, style = MaterialTheme.typography.bodyMedium)
                    if (impersonated.isNotEmpty()) {
                        Text(
                            impersonationWarning(impersonated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (exact.isEmpty()) {
                        // Always in the error color: an unlinked app or site is the realistic phishing case.
                        Text(
                            fillWarning(target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar en la bóveda") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                    if (linkable) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rememberChoice && canRemember,
                                onCheckedChange = { rememberChoice = it },
                                enabled = canRemember,
                            )
                            Text("Vincular la entrada que elija a ${target.label}")
                        }
                    }
                    viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (query.isNotBlank()) {
                section("Resultados", searchResults, viewModel.busy, ::fill)
                if (searchResults.isEmpty()) {
                    item { Text("Nada coincide con «$query».", modifier = Modifier.padding(16.dp)) }
                }
            } else {
                section("Vinculadas a ${target.label}", exact, viewModel.busy, ::fill)
                section("Quizá sea una de estas", suggested, viewModel.busy, ::fill)
                section("Todas", others, viewModel.busy, ::fill)
                if (entries.isEmpty()) {
                    item { Text(emptyText, modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    entries: List<VaultEntry>,
    busy: Boolean,
    onPick: (VaultEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    item {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(entries, key = { "$title/${it.id}" }) { entry ->
        ListItem(
            headlineContent = { Text(entry.title.ifBlank { "(sin nombre)" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = if (entry.username.isNotEmpty()) {
                { Text(entry.username, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                null
            },
            modifier = Modifier.clickable(enabled = !busy) { onPick(entry) },
        )
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveEntryScreen(
    entries: List<VaultEntry>,
    pending: PendingSave,
    viewModel: AutofillViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val matches = remember(entries, pending) { CredentialMatcher.exactMatches(entries, pending.target) }
    val impersonated = remember(entries, pending) { CredentialMatcher.impersonationWarnings(entries, pending.target) }
    // The user name was typed in the other app: shown (and saved) without invisible characters.
    val typedUsername = remember(pending) { ExternalText.sanitize(pending.username) }
    // Decided once, from the entries as they were when the screen opened, so a successful save
    // doesn't flip the screen into this message before it closes.
    val alreadyStored = remember(pending) { SaveCapture.alreadyStored(matches, typedUsername, pending.password) }
    if (alreadyStored != null) {
        MessageScreen(
            title = "Ya está en Bóveda",
            text = "«${alreadyStored.title.ifBlank { "(sin nombre)" }}» ya guarda este usuario y esta contraseña " +
                "para ${pending.target.label}. No hay nada que cambiar.",
            onClose = onCancel,
        )
        return
    }
    var title by remember { mutableStateOf(CredentialMatcher.suggestedTitle(pending.target, entries)) }
    var username by remember { mutableStateOf(typedUsername) }
    // "Actualizar" only comes preselected for an exact match of the destination with the same user.
    var replaceId by remember { mutableStateOf(SaveCapture.preselect(matches, typedUsername)?.id) }
    var revealed by remember { mutableStateOf(false) }
    val existing = replaceId?.let { id -> matches.find { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guardar en Bóveda") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancelar") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (existing != null) {
                    "Credenciales de ${pending.target.label}. Se actualizará «${existing.title}». " +
                        SaveCapture.changeSummary(existing, username, pending.password)
                } else {
                    "Credenciales de ${pending.target.label}. La contraseña (${pending.password.length} caracteres) " +
                        "se guardará cifrada."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            // What is about to be stored is never a blind overwrite: the user can compare both values.
            TextButton(onClick = { revealed = !revealed }) {
                Text(if (revealed) "Ocultar" else "Mostrar")
            }
            if (revealed) {
                Text(
                    "Capturada: ${pending.password}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                if (existing != null) {
                    Text(
                        "Actual: ${existing.password.ifEmpty { "(vacía)" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            idnWarning(pending.target)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            if (impersonated.isNotEmpty()) {
                Text(
                    "${impersonationWarning(impersonated)} Se guardará sin vincular.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                unlinkableReason(pending.target)?.let { reason ->
                    Text(
                        "$reason Se guardará sin vincular: tendrás que elegirla a mano al rellenar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (matches.isNotEmpty()) {
                Text("¿Dónde la guardo?", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(label = "En una entrada nueva", selected = replaceId == null) { replaceId = null }
                matches.forEach { entry ->
                    ChoiceRow(
                        label = "Actualizar «${entry.title}» (${entry.username.ifEmpty { "sin usuario" }})",
                        selected = replaceId == entry.id,
                    ) { replaceId = entry.id }
                }
            }
            if (replaceId == null) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NoLearningTextField(
                value = username,
                onValueChange = { username = it },
                label = "Usuario o email",
                // Empty while updating keeps the stored user, which is shown here as a hint.
                placeholder = existing?.username?.takeIf { it.isNotEmpty() },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
            )
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { viewModel.save(pending, title, username, replaceId, onDone) },
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar")
            }
            OutlinedButton(onClick = onCancel, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) {
                Text("No guardar")
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun MessageScreen(title: String, text: String, onClose: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(text)
            Button(onClick = onClose) { Text("Cerrar") }
        }
    }
}

/** What a fill request will write, from the ids the system asked for. */
private fun fillDescription(request: AutofillRequest.Fill): String = when {
    request.usernameId != null && request.passwordId != null -> "Se rellenarán usuario y contraseña."
    request.passwordId != null -> "Solo la contraseña."
    else -> "Solo el usuario."
}

/**
 * Why a target can't be linked to an entry, or null if it can. The claimed domain was sanitized
 * by TargetResolver; one left empty by that is named as such instead of echoing nothing.
 */
private fun unlinkableReason(target: AutofillTarget): String? {
    val claimed = target.claimedWebDomain?.ifBlank { "dirección ilegible" }
    val certificates = target.certificates
    return when {
        target.unencrypted ->
            "Página sin cifrar: «$claimed» se abre por http, no https, así que cualquiera en la red " +
                "podría estar sirviendo este formulario."
        claimed != null && certificates != null && TrustedBrowsers.isTrusted(target.packageName, certificates) ->
            "La dirección de esta página («$claimed») no es un dominio web normal."
        claimed != null ->
            "Esta app muestra una página web («$claimed») pero no es un navegador reconocido, " +
                "así que Bóveda no se fía de esa dirección."
        certificates == null -> "No se ha podido verificar la firma de esta app."
        target.trustedBrowser ->
            "El navegador no ha indicado qué web muestra, así que un vínculo a él alcanzaría " +
                "cualquier página sin dirección que abra."
        else -> null
    }
}

/** Shown when the domain has non-ASCII characters: a look-alike of a real domain can hide there. */
private fun idnWarning(target: AutofillTarget): String? =
    if (target.isIdn) {
        "Dominio internacionalizado: su nombre real tiene caracteres no latinos y se muestra en su " +
            "forma ASCII («${target.host}»). Puede imitar a un dominio conocido: compruébalo con cuidado."
    } else {
        null
    }

/**
 * Shown when the app asking has the package name of an app linked to [entries] but another
 * signature: Android allows one signer per package name, so this is almost certainly a fake.
 */
private fun impersonationWarning(entries: List<VaultEntry>): String {
    val titles = entries.joinToString(", ") { "«${it.title.ifBlank { "(sin nombre)" }}»" }
    return "Esta app tiene el mismo nombre que la vinculada a $titles pero OTRA firma digital: " +
        "probablemente es falsa. No se podrá vincular."
}

/** Shown when no entry is linked to the app or site asking to be filled. */
private fun fillWarning(target: AutofillTarget): String =
    unlinkableReason(target)?.let { "$it Elige solo si sabes qué app es; no se podrá vincular." }
        ?: if (target.host != null) {
            "No hay ninguna entrada vinculada a esta web. Comprueba bien la dirección antes de elegir."
        } else {
            "No hay ninguna entrada vinculada a esta app. Una app falsa podría imitar a la de tu banco: " +
                "comprueba que es la que esperas antes de elegir."
        }
