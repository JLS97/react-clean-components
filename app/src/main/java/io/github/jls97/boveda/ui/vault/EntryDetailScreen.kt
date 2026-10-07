package io.github.jls97.boveda.ui.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.ui.components.BotonCopiar
import io.github.jls97.boveda.ui.components.BotonIcono
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.Ficha
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.OnAppBackground
import io.github.jls97.boveda.ui.components.Pantalla
import io.github.jls97.boveda.ui.components.Salida
import io.github.jls97.boveda.ui.components.autofillTargetLabel
import io.github.jls97.boveda.ui.components.formatDate
import io.github.jls97.boveda.ui.theme.Comportamiento
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * La ficha de una entrada. Aquí vive lo que la ficha recuerda mientras se ve (si la contraseña
 * está a la vista, si se está confirmando el borrado); lo que se ve es [FichaContenido].
 */
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
    // Como en los campos de contraseña, lo revelado se vuelve a ocultar solo pasado un rato.
    LaunchedEffect(revealPassword) {
        if (revealPassword) {
            delay(Comportamiento.CONTRASENA_REVELADA_SEGUNDOS * 1_000L)
            revealPassword = false
        }
    }

    FichaContenido(
        entry = entry,
        busy = busy,
        passwordRevealed = revealPassword,
        onTogglePassword = { revealPassword = !revealPassword },
        onBack = onBack,
        onEdit = onEdit,
        onDelete = { confirmDelete = true },
        onCopy = onCopy,
        snackbar = snackbar,
        otpSection = otpSection,
    )

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
            peligro = true,
        )
    }
}

/**
 * La ficha, sin estado: los datos como un impreso (etiqueta pequeña encima, valor debajo y líneas
 * de puntos entre ellos), el código 2FA de [otpSection], las notas, los destinos del autorrelleno
 * y, al pie, las fechas. [onDelete] solo pide la confirmación.
 *
 * Ningún valor se puede seleccionar: solo llegan al portapapeles con «Copiar», que los marca como
 * sensibles y los borra pasado un rato.
 */
@Composable
internal fun FichaContenido(
    entry: VaultEntry,
    busy: Boolean,
    passwordRevealed: Boolean,
    onTogglePassword: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    snackbar: SnackbarHostState? = null,
    otpSection: @Composable () -> Unit = {},
    scroll: ScrollState = rememberScrollState(),
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Pantalla(
        titulo = entry.title.ifBlank { "(sin nombre)" },
        salida = Salida(onBack),
        antetitulo = "Ficha",
        entradilla = entry.url.ifEmpty { null },
        acciones = {
            BotonIcono(R.drawable.ic_editar, "Editar", onEdit, enabled = !busy, tinte = c.textPrimary)
            BotonIcono(
                R.drawable.ic_papelera,
                "Eliminar",
                onDelete,
                Modifier.padding(end = Spacing.s1),
                enabled = !busy,
                tinte = c.dangerFg,
            )
        },
        snackbar = snackbar,
        ocupado = busy,
        scroll = scroll,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Datos(entry, passwordRevealed, onTogglePassword, onCopy)
            otpSection()
            if (entry.notes.isNotEmpty()) {
                Ficha {
                    Column(Modifier.semantics(mergeDescendants = true) { }) {
                        EtiquetaDato("Notas")
                        Text(entry.notes, style = t.body, color = c.textPrimary, modifier = Modifier.padding(top = Spacing.s1))
                    }
                }
            }
            if (entry.autofillTargets.isNotEmpty()) Destinos(entry.autofillTargets)
        }
        LineaPunteada(Modifier.padding(top = Spacing.s8, bottom = Spacing.s3))
        Text(
            "Creada el ${formatDate(entry.createdAt)} · Modificada el ${formatDate(entry.updatedAt)}",
            style = t.caption,
            color = c.textTertiary,
        )
    }
}

/** Usuario, contraseña y web, cada uno con sus botones; sin ninguno de los tres no hay ficha. */
@Composable
private fun Datos(
    entry: VaultEntry,
    passwordRevealed: Boolean,
    onTogglePassword: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val conUsuario = entry.username.isNotEmpty()
    val conContrasena = entry.password.isNotEmpty()
    val conWeb = entry.url.isNotEmpty()
    if (!conUsuario && !conContrasena && !conWeb) return
    // A la derecha ya dan aire los botones de icono: menos relleno de ese lado.
    Ficha(relleno = PaddingValues(start = Spacing.s4, end = Spacing.s1, top = Spacing.s1, bottom = Spacing.s1)) {
        if (conUsuario) {
            Dato("Usuario o email", acciones = { BotonCopiar("Copiar usuario", { onCopy("Usuario", entry.username) }) }) {
                Text(entry.username, style = t.bodyLarge, color = c.textPrimary)
            }
        }
        if (conContrasena) {
            if (conUsuario) Separacion()
            Dato(
                "Contraseña",
                acciones = {
                    BotonIcono(
                        icono = if (passwordRevealed) R.drawable.ic_ojo_tachado else R.drawable.ic_ojo,
                        descripcion = if (passwordRevealed) "Ocultar contraseña" else "Mostrar contraseña",
                        onClick = onTogglePassword,
                        tamanoIcono = Sizes.iconMd,
                    )
                    BotonCopiar("Copiar contraseña", { onCopy("Contraseña", entry.password) })
                },
            ) { ValorContrasena(entry.password, passwordRevealed) }
        }
        if (conWeb) {
            if (conUsuario || conContrasena) Separacion()
            Dato("Web o app", acciones = { BotonCopiar("Copiar dirección", { onCopy("Dirección", entry.url) }) }) {
                Text(entry.url, style = t.bodyLarge, color = c.textPrimary)
            }
        }
    }
}

/** Un dato del impreso: etiqueta y valor, que TalkBack lee juntos, y sus botones a la derecha. */
@Composable
private fun Dato(
    etiqueta: String,
    acciones: @Composable RowScope.() -> Unit,
    valor: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.listItemHeight).padding(vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.s2).semantics(mergeDescendants = true) { }) {
            EtiquetaDato(etiqueta)
            Column(Modifier.padding(top = Spacing.s1), content = valor)
        }
        acciones()
    }
}

/** La etiqueta pequeña que va encima de cada dato. */
@Composable
private fun EtiquetaDato(texto: String) {
    Text(texto, style = ContrasenoraTheme.type.label, color = ContrasenoraTheme.colors.textSecondary)
}

/** Línea de puntos entre dos datos; acaba donde acaban los iconos, no en el borde. */
@Composable
private fun Separacion() = LineaPunteada(Modifier.padding(end = Spacing.s3))

/**
 * La contraseña: doce puntos (no dicen cuánto mide) o, revelada, en Atkinson Mono con las cifras
 * en latón y los símbolos en ciruela, en renglones iguales si es larga. El cambio funde y asienta
 * el texto, y la ficha crece con él.
 */
@Composable
private fun ValorContrasena(password: String, revealed: Boolean) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = revealed,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None using SizeTransform { _, _ -> snap() }
            } else {
                (
                    fadeIn(tween(Motion.BASE, easing = Motion.Standard)) +
                        slideInVertically(tween(Motion.BASE, easing = Motion.Standard)) { alto -> alto / 4 }
                    ) togetherWith fadeOut(tween(Motion.FAST, easing = Motion.Exit)) using
                    SizeTransform(clip = false) { _, _ -> tween(Motion.BASE, easing = Motion.Standard) }
            }
        },
        contentAlignment = Alignment.TopStart,
        label = "contraseña revelada",
    ) { visible ->
        if (visible) {
            SecretoEnRenglones(password, t.secret)
        } else {
            Text(
                PUNTOS,
                style = t.secret,
                color = c.textPrimary,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = "Oculta" },
            )
        }
    }
}

/** Lo que se ve de una contraseña oculta: siempre lo mismo, mida lo que mida. */
private const val PUNTOS = "••••••••••••"

/** Los destinos del autorrelleno, uno por fila con su llave. */
@Composable
private fun Destinos(destinos: List<String>) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Ficha(relleno = PaddingValues(start = Spacing.s4, end = Spacing.s4, top = Spacing.s4, bottom = Spacing.s1)) {
        EtiquetaDato("Autorrelleno vinculado a")
        destinos.forEachIndexed { i, destino ->
            if (i > 0) LineaPunteada()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Icon(painterResource(R.drawable.ic_llave), contentDescription = null, tint = c.textLink, modifier = Modifier.size(Sizes.iconMd))
                Text(autofillTargetLabel(destino), style = t.body, color = c.textPrimary)
            }
        }
    }
}
