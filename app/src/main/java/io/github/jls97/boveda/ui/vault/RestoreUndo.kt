package io.github.jls97.boveda.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.PasswordPromptDialog

/** Qué se pidió sobre la última restauración, a la espera de confirmación. */
enum class UndoAction { UNDO, DISCARD }

/**
 * Aviso en la lista mientras se conserva la bóveda que sustituyó la última restauración (B-31):
 * recuerda que la contraseña maestra es ahora la de la copia y ofrece deshacer la restauración o
 * descartar esa copia. Desaparece con cualquiera de las dos.
 */
@Composable
fun RestoreUndoBanner(viewModel: VaultViewModel, onRestoreUndone: () -> Unit) {
    if (!viewModel.canUndoRestore) return
    var action by remember { mutableStateOf<UndoAction?>(null) }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "Este teléfono conserva la bóveda anterior: la que estaba en su sitio antes de la última " +
                    "restauración o del último cambio. Cada una se abre con su propia contraseña maestra y, " +
                    "al cambiar de una a otra, la huella se desactiva.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { action = UndoAction.UNDO }, enabled = !viewModel.busy) {
                    Text("Volver a la anterior")
                }
                TextButton(onClick = { action = UndoAction.DISCARD }, enabled = !viewModel.busy) {
                    Text("Descartar la anterior")
                }
            }
        }
    }
    RestoreUndoDialogs(action, viewModel, onRestoreUndone) { action = null }
}

/**
 * Confirmaciones de las dos acciones sobre la última restauración. Deshacer pide además la
 * contraseña maestra, como las demás operaciones sensibles de Ajustes (B-35); descartar borra la
 * copia anterior para siempre, así que solo se confirma.
 */
@Composable
fun RestoreUndoDialogs(
    action: UndoAction?,
    viewModel: VaultViewModel,
    onRestoreUndone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var undoConfirmed by remember(action) { mutableStateOf(false) }
    when (action) {
        null -> Unit
        UndoAction.UNDO -> if (!undoConfirmed) {
            ConfirmDialog(
                title = "¿Volver a la bóveda anterior?",
                text = "La bóveda de antes de la última restauración volverá a su sitio, bloqueada: se abre " +
                    "con su propia contraseña maestra y la huella quedará desactivada. La bóveda de ahora se " +
                    "guarda en su lugar, así que podrás volver a cambiar.",
                confirmLabel = "Continuar",
                onConfirm = { undoConfirmed = true },
                onDismiss = onDismiss,
            )
        } else {
            PasswordPromptDialog(
                title = "Volver a la bóveda anterior",
                text = "Escribe la contraseña maestra para confirmar que eres tú.",
                confirmLabel = "Deshacer",
                label = "Contraseña maestra",
                onConfirm = { password ->
                    onDismiss()
                    viewModel.verifyMasterPassword(password) { viewModel.undoRestore(onRestoreUndone) }
                },
                onDismiss = onDismiss,
            )
        }
        UndoAction.DISCARD -> ConfirmDialog(
            title = "¿Descartar la bóveda anterior?",
            text = "Se borrará para siempre la bóveda anterior que se conserva en este teléfono. No se puede deshacer.",
            confirmLabel = "Descartar",
            onConfirm = {
                onDismiss()
                viewModel.discardUndo()
            },
            onDismiss = onDismiss,
        )
    }
}
