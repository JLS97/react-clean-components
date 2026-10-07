package io.github.jls97.boveda.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.R
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import java.util.Locale

/** Tipo de aviso. La palabra del sello dice el estado, así que nunca depende solo del color. */
enum class TipoAviso(val palabra: String) {
    Exito("Conforme"),
    Aviso("Ojo"),
    Peligro("Urgente"),
    Info("Nota"),
}

/**
 * Aviso de Contraseñora: una tarjeta neutra, como un papel, con un sello de goma estampado. El
 * color del estado vive solo en el sello. Los de Peligro llevan además un borde fino del mismo
 * color y TalkBack los anuncia en cuanto aparecen; el resto, cuando termina lo que está leyendo.
 *
 * El título dice qué ha pasado; el mensaje, qué hacer; la acción, el verbo exacto.
 */
@Composable
fun Aviso(
    tipo: TipoAviso,
    titulo: String,
    modifier: Modifier = Modifier,
    mensaje: String? = null,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
    /** Segunda acción opcional, a la derecha de la primera. */
    accionSecundaria: String? = null,
    onAccionSecundaria: (() -> Unit)? = null,
    accionesActivas: Boolean = true,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val tinta = tintaDe(tipo)
    val critico = tipo == TipoAviso.Peligro
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = if (critico) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        shape = ContrasenoraShapes.md,
        color = c.bgSurface,
        contentColor = c.textPrimary,
        border = BorderStroke(if (critico) Sizes.inputBorder else 1.dp, if (critico) tinta else c.borderSubtle),
    ) {
        Column(Modifier.padding(start = Spacing.s4, end = Spacing.s3, top = Spacing.s4, bottom = Spacing.s3)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                // El sello se lee primero: «Urgente. Título.»
                Text(
                    text = titulo,
                    style = t.bodyStrong,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "${tipo.palabra}. $titulo" },
                )
                Sello(texto = tipo.palabra, tinta = tinta, modifier = Modifier.clearAndSetSemantics { })
            }
            if (mensaje != null) {
                Text(mensaje, style = t.small, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s1, end = Spacing.s1))
            }
            if ((accion != null && onAccion != null) || (accionSecundaria != null && onAccionSecundaria != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                    if (accion != null && onAccion != null) AccionAviso(accion, onAccion, accionesActivas)
                    if (accionSecundaria != null && onAccionSecundaria != null) {
                        AccionAviso(accionSecundaria, onAccionSecundaria, accionesActivas)
                    }
                }
            } else {
                Spacer(Modifier.height(Spacing.s1))
            }
        }
    }
}

@Composable
private fun AccionAviso(texto: String, onClick: () -> Unit, enabled: Boolean) {
    val c = ContrasenoraTheme.colors
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier.heightIn(min = Sizes.touchTarget),
    ) {
        Text(
            texto,
            style = ContrasenoraTheme.type.label.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) c.textLink else c.textDisabled,
        )
    }
}

/**
 * Sello de goma: doble borde, girado −7°, que se «estampa» al aparecer (escala 1,3 → 1 en 220 ms).
 * Se puede usar suelto, por ejemplo «Conforme» junto a algo recién guardado.
 */
@Composable
fun Sello(texto: String, tinta: Color, modifier: Modifier = Modifier, girado: Float = -7f) {
    val reduced = rememberReducedMotion()
    val estampado = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) estampado.animateTo(1f, tween(Motion.BASE, easing = Motion.Emphasized))
    }
    Box(
        modifier = modifier
            .padding(top = Spacing.s0_5)
            .graphicsLayer {
                val p = estampado.value
                val escala = 1.3f - 0.3f * p
                scaleX = escala
                scaleY = escala
                alpha = 0.92f * p
                rotationZ = girado
            }
            .border(2.dp, tinta, ContrasenoraShapes.selloExterior)
            .padding(2.dp)
            .border(1.dp, tinta, ContrasenoraShapes.selloInterior)
            .padding(horizontal = Spacing.s2, vertical = 3.dp),
    ) {
        Text(
            text = texto.uppercase(Locale.forLanguageTag("es-ES")),
            style = ContrasenoraTheme.type.sello,
            color = tinta,
            maxLines = 1,
        )
    }
}

/** Color de la tinta del sello de cada tipo de aviso. */
@Composable
fun tintaDe(tipo: TipoAviso): Color {
    val c = ContrasenoraTheme.colors
    return when (tipo) {
        TipoAviso.Exito -> c.successFg
        TipoAviso.Aviso -> c.warningFg
        TipoAviso.Peligro -> c.dangerFg
        TipoAviso.Info -> c.infoFg
    }
}

/**
 * Un error sin campo al que pegarse (por ejemplo, de la huella o de una operación): con su icono,
 * nunca solo el color, y anunciado a TalkBack cuando aparece.
 */
@Composable
fun TextoError(texto: String, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painterResource(R.drawable.ic_alerta),
            contentDescription = null,
            tint = c.dangerFg,
            modifier = Modifier.padding(top = 3.dp, end = 6.dp).size(Sizes.iconSm),
        )
        Text(texto, style = ContrasenoraTheme.type.small, color = c.dangerFg)
    }
}

/** «Primera frase. Lo demás.» → título de un aviso y su mensaje (null si solo hay una frase). */
fun partirEnAviso(texto: String): Pair<String, String?> {
    val corte = texto.indexOf(". ")
    return if (corte < 0) texto to null else texto.substring(0, corte + 1) to texto.substring(corte + 2)
}
