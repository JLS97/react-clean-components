package io.github.jls97.boveda.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.core.generator.GeneratorOptions
import io.github.jls97.boveda.core.generator.PasswordGenerator
import io.github.jls97.boveda.core.generator.PasswordStrength
import io.github.jls97.boveda.ui.components.BackButton
import io.github.jls97.boveda.ui.components.strengthLabel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    password: String,
    options: GeneratorOptions,
    forEditor: Boolean,
    onOptionsChange: (GeneratorOptions) -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onUse: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val entropy = PasswordGenerator.entropyBits(options)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generador") },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = password.ifEmpty { "—" },
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (password.isNotEmpty()) {
                Text(
                    "≈ ${entropy.roundToInt()} bits de entropía · " +
                        strengthLabel(PasswordStrength.level(entropy)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onRegenerate) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("Otra", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onCopy, enabled = password.isNotEmpty()) { Text("Copiar") }
                if (forEditor) {
                    Button(onClick = onUse, enabled = password.isNotEmpty()) { Text("Usar") }
                }
            }

            Text("Longitud: ${options.length}", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = options.length.toFloat(),
                onValueChange = { onOptionsChange(options.copy(length = it.roundToInt())) },
                valueRange = GeneratorOptions.MIN_LENGTH.toFloat()..GeneratorOptions.MAX_LENGTH.toFloat(),
                steps = GeneratorOptions.MAX_LENGTH - GeneratorOptions.MIN_LENGTH - 1,
            )
            OptionSwitch("Minúsculas (a-z)", options.lowercase) { onOptionsChange(options.copy(lowercase = it)) }
            OptionSwitch("Mayúsculas (A-Z)", options.uppercase) { onOptionsChange(options.copy(uppercase = it)) }
            OptionSwitch("Números (0-9)", options.digits) { onOptionsChange(options.copy(digits = it)) }
            OptionSwitch("Símbolos (!#\$%…)", options.symbols) { onOptionsChange(options.copy(symbols = it)) }
            OptionSwitch("Evitar caracteres parecidos (I l 1 O 0)", options.avoidAmbiguous) {
                onOptionsChange(options.copy(avoidAmbiguous = it))
            }
            if (!PasswordGenerator.canGenerate(options)) {
                Text("Activa al menos un tipo de carácter.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
