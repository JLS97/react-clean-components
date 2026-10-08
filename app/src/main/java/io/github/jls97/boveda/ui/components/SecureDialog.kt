package io.github.jls97.boveda.ui.components

import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme

/**
 * AlertDialog de Material3 con la misma protección que la ventana de la Activity.
 *
 * El diálogo de Compose se dibuja en una ventana propia (DialogWrapper), con su DecorView y su
 * AndroidComposeView, así que la exclusión del autorrelleno, el filtro de toques y la ocultación de
 * superposiciones que MainActivity y AutofillActivity aplican a su decorView no lo alcanzan: un
 * servicio de autorrelleno ajeno recibiría los campos de contraseña maestra del diálogo con su valor.
 * Aquí se aplica la misma política sobre la ventana del diálogo. FLAG_SECURE sí se hereda solo.
 */
@Composable
fun SecureAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        // El botón de confirmación es el único hueco obligatorio: la protección va siempre con él.
        confirmButton = {
            HardenDialogWindow()
            confirmButton()
        },
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = ContrasenoraShapes.lg,
        containerColor = ContrasenoraTheme.colors.bgRaised,
        titleContentColor = ContrasenoraTheme.colors.textPrimary,
        textContentColor = ContrasenoraTheme.colors.textSecondary,
        tonalElevation = 0.dp,
    )
}

/** Excluye del autorrelleno de terceros, filtra toques con superposición y oculta las superposiciones. */
@Composable
private fun HardenDialogWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val root = view.rootView
        root.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        root.filterTouchesWhenObscured = true
        view.dialogWindow()?.setHideOverlayWindows(true)
        onDispose {}
    }
}

/** Ventana del diálogo de Compose que contiene esta vista, o null si no está dentro de uno. */
private fun View.dialogWindow(): Window? {
    var parent: ViewParent? = parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return null
}
