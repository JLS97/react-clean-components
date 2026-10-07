package io.github.jls97.boveda.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion

// El texto de un botón dice exactamente lo que hace («Guardar contraseña»), nunca un chiste.

private fun alto(compacto: Boolean) = if (compacto) Sizes.buttonHeightCompact else Sizes.buttonHeight
private val relleno = PaddingValues(horizontal = Spacing.s5)

/** Al pulsar, el botón se hunde un poco (escala 0,97), como una tecla. Sin animación si se han quitado. */
@Composable
private fun Modifier.hundir(interaccion: MutableInteractionSource): Modifier {
    val pulsado by interaccion.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val escala by animateFloatAsState(
        targetValue = if (pulsado && !reduced) 0.97f else 1f,
        animationSpec = tween(Motion.FAST, easing = Motion.Standard),
        label = "hundir",
    )
    return graphicsLayer {
        scaleX = escala
        scaleY = escala
    }
}

@Composable
private fun RowScope.ContenidoBoton(texto: String, @DrawableRes icono: Int?) {
    if (icono != null) {
        Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(Sizes.iconMd))
        Spacer(Modifier.width(Spacing.s2))
    }
    Text(texto, style = ContrasenoraTheme.type.bodyStrong, textAlign = TextAlign.Center)
}

@Composable
fun BotonPrimario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compacto: Boolean = false,
    @DrawableRes icono: Int? = null,
) {
    val c = ContrasenoraTheme.colors
    val interaccion = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = alto(compacto)).hundir(interaccion),
        enabled = enabled,
        shape = ContrasenoraShapes.sm,
        contentPadding = relleno,
        interactionSource = interaccion,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.brandPrimary,
            contentColor = c.brandOnPrimary,
            disabledContainerColor = c.bgSunken,
            disabledContentColor = c.textDisabled,
        ),
    ) { ContenidoBoton(texto, icono) }
}

@Composable
fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compacto: Boolean = false,
    @DrawableRes icono: Int? = null,
) {
    val c = ContrasenoraTheme.colors
    val interaccion = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = alto(compacto)).hundir(interaccion),
        enabled = enabled,
        shape = ContrasenoraShapes.sm,
        contentPadding = relleno,
        interactionSource = interaccion,
        border = BorderStroke(Sizes.inputBorder, if (enabled) c.borderStrong else c.borderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary, disabledContentColor = c.textDisabled),
    ) { ContenidoBoton(texto, icono) }
}

/** Solo texto. Con [peligro], en el color de peligro (confirmaciones destructivas de un diálogo). */
@Composable
fun BotonFantasma(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    peligro: Boolean = false,
    @DrawableRes icono: Int? = null,
) {
    val c = ContrasenoraTheme.colors
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.touchTarget),
        enabled = enabled,
        shape = ContrasenoraShapes.sm,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (peligro) c.dangerFg else c.textLink,
            disabledContentColor = c.textDisabled,
        ),
    ) { ContenidoBoton(texto, icono) }
}

/** Solo para acciones destructivas. Confirmar siempre con un diálogo en tono sobrio. */
@Composable
fun BotonPeligro(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icono: Int? = null,
) {
    val c = ContrasenoraTheme.colors
    val interaccion = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.buttonHeight).hundir(interaccion),
        enabled = enabled,
        shape = ContrasenoraShapes.sm,
        contentPadding = relleno,
        interactionSource = interaccion,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.dangerSolid,
            contentColor = c.dangerOnSolid,
            disabledContainerColor = c.bgSunken,
            disabledContentColor = c.textDisabled,
        ),
    ) { ContenidoBoton(texto, icono) }
}

/**
 * Botón de icono de 48 dp con su etiqueta para TalkBack. [tinte] por defecto es el del texto
 * secundario; [fondo] pinta un círculo detrás (por ejemplo, para el candado de la cabecera).
 */
@Composable
fun BotonIcono(
    @DrawableRes icono: Int,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tinte: Color = ContrasenoraTheme.colors.textSecondary,
    fondo: Color = Color.Transparent,
    tamanoIcono: Dp = Sizes.iconLg,
) {
    val c = ContrasenoraTheme.colors
    val interaccion = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Sizes.touchTarget).hundir(interaccion),
        enabled = enabled,
        interactionSource = interaccion,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = fondo,
            contentColor = tinte,
            disabledContentColor = c.textDisabled,
        ),
    ) {
        Icon(painterResource(icono), contentDescription = descripcion, modifier = Modifier.size(tamanoIcono))
    }
}
