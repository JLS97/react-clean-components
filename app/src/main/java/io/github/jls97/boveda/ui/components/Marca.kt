package io.github.jls97.boveda.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Palette
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion

// Geometría de logo/svg/isotipo.svg (caja de 256 × 256). Se dibuja en Canvas, y no con el
// VectorDrawable, para poder levantar el arco al desbloquear: el momento de marca de la guía.
private fun trazado(datos: String): Path = PathParser().parsePathString(datos).toPath()

private val Arco = trazado("M86 128V100a42 42 0 0 1 84 0v28")
private val Brillo = trazado("M112 36c4-10 22-12 30-2")
private val Cerradura = trazado("M128 189a11 11 0 0 1 6.5 19.9L138 222h-20l3.5-13.1A11 11 0 0 1 128 189z")
private val Gafas = listOf(
    trazado("M78 150c6-6 30-8 40-2c3 10-1 24-16 25c-15 1-26-10-24-23z"),
    trazado("M178 150c-6-6-30-8-40-2c-3 10 1 24 16 25c15 1 26-10 24-23z"),
    trazado("M118 151c6-4 14-4 20 0"),
    trazado("M79 151l-13-14M177 151l13-14"),
)

/** Lo que sube el arco del candado cuando está abierto del todo. */
private val SubidaArco = 6.dp

/**
 * El isotipo de Contraseñora: un candado que es a la vez una señora, con moño, gafas de ojo de
 * gato y la cerradura por boca. Cambia de colores con el tema, como las variantes «sobre oscuro».
 *
 * @param apertura de 0 (cerrado) a 1 (el arco y el moño han subido 6 dp): se anima al desbloquear.
 * @param simple sin gafas, como el isotipo simple, para tamaños por debajo de 32 dp.
 * @param descripcion texto para TalkBack; null si el isotipo es decorativo.
 */
@Composable
fun Isotipo(
    modifier: Modifier = Modifier,
    apertura: Float = 0f,
    simple: Boolean = false,
    descripcion: String? = null,
) {
    val c = ContrasenoraTheme.colors
    val cuerpo = c.brandPrimary
    val gafas = if (c.isDark) Palette.Ciruela700 else Palette.Rulo300
    val hueco = if (c.isDark) Palette.Noche else Palette.Luto50
    val semantica = if (descripcion != null) {
        Modifier.semantics { contentDescription = descripcion }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Canvas(modifier.then(semantica)) {
        dibujarIsotipo(cuerpo, gafas, hueco, apertura.coerceIn(0f, 1f), simple)
    }
}

private fun DrawScope.dibujarIsotipo(cuerpo: Color, gafas: Color, hueco: Color, apertura: Float, simple: Boolean) {
    val escala = size.minDimension / 256f
    if (escala <= 0f) return
    val subida = apertura * SubidaArco.toPx() / escala
    withTransform({
        translate((size.width - 256f * escala) / 2f, (size.height - 256f * escala) / 2f)
        scale(escala, escala, pivot = Offset.Zero)
    }) {
        // El moño corona el arco: suben juntos.
        translate(top = -subida) {
            drawOval(cuerpo, topLeft = Offset(103f, 17f), size = Size(50f, 42f))
            drawPath(Arco, cuerpo, style = Stroke(width = 20f))
        }
        drawRoundRect(cuerpo, topLeft = Offset(50f, 114f), size = Size(156f, 124f), cornerRadius = CornerRadius(36f, 36f))
        if (!simple) {
            val trazo = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            Gafas.forEach { drawPath(it, gafas, style = trazo) }
        }
        translate(top = -subida) {
            drawPath(Brillo, hueco.copy(alpha = 0.55f), style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
        drawPath(Cerradura, hueco)
    }
}

/**
 * «Trabajando…»: el candado pequeño abre y cierra el arco mientras dura una operación lenta
 * (cifrar, descifrar, comprobar la contraseña maestra). Sustituye a la rueda de Material.
 */
@Composable
fun Trabajando(texto: String, modifier: Modifier = Modifier, tamano: Dp = 28.dp) {
    val reduced = rememberReducedMotion()
    val apertura = if (reduced) {
        0f
    } else {
        val transicion = rememberInfiniteTransition(label = "trabajando")
        val valor by transicion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "arco",
        )
        valor
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Isotipo(Modifier.size(tamano), apertura = apertura, simple = tamano < 32.dp)
        Text(texto, style = ContrasenoraTheme.type.body, color = ContrasenoraTheme.colors.textSecondary)
    }
}
