package io.github.jls97.boveda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Diálogo de restauración cuando ya hay una bóveda en el teléfono (B-31): pide la contraseña maestra
 * de la copia y la contraseña maestra actual de este teléfono, las dos, porque sustituir la bóveda
 * exige demostrar que es de quien restaura. [onForgotCurrent], si se da, añade el camino «No
 * recuerdo la contraseña actual» con la contraseña de la copia ya escrita; solo la pantalla de
 * bloqueo lo ofrece, detrás de una confirmación fuerte.
 */
@Composable
fun RestoreBackupDialog(
    text: String,
    onConfirm: (backupPassword: String, currentPassword: String) -> Unit,
    onDismiss: () -> Unit,
    onForgotCurrent: ((backupPassword: String) -> Unit)? = null,
) {
    var backupPassword by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    val complete = backupPassword.isNotEmpty() && currentPassword.isNotEmpty()
    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restaurar copia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text)
                PasswordField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = "Contraseña maestra de la copia",
                )
                PasswordField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = "Contraseña maestra actual de este teléfono",
                    imeAction = ImeAction.Done,
                    onImeAction = { if (complete) onConfirm(backupPassword, currentPassword) },
                )
                if (onForgotCurrent != null) {
                    BotonFantasma("No recuerdo la contraseña actual", { onForgotCurrent(backupPassword) }, enabled = backupPassword.isNotEmpty())
                }
            }
        },
        confirmButton = {
            BotonFantasma("Restaurar", { onConfirm(backupPassword, currentPassword) }, enabled = complete, peligro = true)
        },
        dismissButton = { BotonFantasma("Cancelar", onDismiss) },
    )
}
