package io.github.jls97.boveda.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.otp.Totp
import io.github.jls97.boveda.ui.theme.Comportamiento
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import io.github.jls97.boveda.ui.theme.voz

/** «45 s», «5 min»: para frases. Por encima del minuto se redondea hacia arriba. */
fun tiempoEnFrase(segundos: Long): String =
    if (segundos < 60) "$segundos s" else "${(segundos + 59) / 60} min"

/** «04:32»: para la cuenta atrás grande. */
fun tiempoEnReloj(segundos: Long): String = "%02d:%02d".format(segundos / 60, segundos % 60)

/**
 * Bloqueo temporal por intentos fallidos: la lápida de «Descanse en Pass», el título y una cuenta
 * atrás bien visible hasta el siguiente intento. En modo Sobria, sin chistes. TalkBack anuncia el
 * título al aparecer, no cada segundo.
 */
@Composable
fun DescanseEnPass(segundosRestantes: Long, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val segundos = segundosRestantes.coerceAtLeast(0)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ilustracion_descanse_en_pass),
            contentDescription = null,
            modifier = Modifier.size(width = 136.dp, height = 142.dp),
        )
        Text(
            text = voz("Descanse en Pass", "Acceso bloqueado temporalmente"),
            style = t.display2,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = Spacing.s5, bottom = Spacing.s3)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Text(
            text = voz(
                "Demasiados intentos fallidos. Este intento ya está criando malvas. " +
                    "Podrás volver a probar en ${tiempoEnFrase(segundos)}.",
                "Demasiados intentos fallidos. Podrás volver a intentarlo en ${tiempoEnFrase(segundos)}.",
            ),
            style = t.bodyLarge,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = tiempoEnReloj(segundos),
            style = t.code,
            color = c.brassText,
            modifier = Modifier
                .padding(top = Spacing.s5)
                .clearAndSetSemantics { },
        )
        Text(
            "para el siguiente intento",
            style = t.caption,
            color = c.textTertiary,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * Código 2FA en vivo: el código en Atkinson Mono agrupado (482 913) y, a la derecha, el anillo de
 * latón con los segundos que le quedan. En los últimos 5 s el anillo pasa a rojo y aparece el
 * aviso de que caduca. TalkBack lo lee cifra a cifra.
 */
@Composable
fun CodigoTotp(
    codigo: String,
    segundosRestantes: Int,
    periodo: Int,
    modifier: Modifier = Modifier,
    etiqueta: String? = null,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val urgente = segundosRestantes <= Comportamiento.TOTP_URGENTE_SEGUNDOS
    val descripcion = "Código 2FA: ${codigo.toList().joinToString(" ")}. Caduca en $segundosRestantes segundos."
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = descripcion },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Column(Modifier.weight(1f).clearAndSetSemantics { }) {
                if (!etiqueta.isNullOrEmpty()) {
                    Text(etiqueta, style = t.small, color = c.textSecondary, maxLines = 1)
                }
                Text(Totp.format(codigo), style = t.code, color = c.totpCode, maxLines = 1)
            }
            AnilloTotp(segundosRestantes, periodo, urgente, Modifier.clearAndSetSemantics { })
        }
        AnimatedVisibility(
            visible = urgente,
            enter = fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.BASE)),
            exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE)),
        ) {
            Text(
                text = voz(
                    "Este está a punto de pasar a mejor vida. Espera al siguiente.",
                    "El código caduca en unos segundos. Espera al siguiente.",
                ),
                style = t.small,
                color = c.dangerFg,
                modifier = Modifier.padding(top = Spacing.s2).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Anillo de cuenta atrás: avanza de forma continua y se rellena de golpe al cambiar de código. */
@Composable
fun AnilloTotp(segundos: Int, periodo: Int, urgente: Boolean, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    val reduced = rememberReducedMotion()
    val fraccion = (segundos / periodo.toFloat()).coerceIn(0f, 1f)
    val anillo = remember { Animatable(fraccion) }
    LaunchedEffect(fraccion) {
        if (reduced || fraccion > anillo.value) {
            anillo.snapTo(fraccion)
        } else {
            anillo.animateTo(fraccion, tween(durationMillis = 1_000, easing = LinearEasing))
        }
    }
    val relleno by animateColorAsState(if (urgente) c.totpRingUrgent else c.totpRingFill, tween(Motion.BASE), label = "anillo")
    Box(modifier.size(Sizes.totpRing + 8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(Sizes.totpRing + 8.dp)) {
            val grosor = Sizes.totpRingStroke.toPx()
            val esquina = Offset(grosor / 2, grosor / 2)
            val area = Size(size.width - grosor, size.height - grosor)
            drawArc(c.totpRingTrack, 0f, 360f, false, topLeft = esquina, size = area, style = Stroke(grosor))
            drawArc(relleno, -90f, 360f * anillo.value, false, topLeft = esquina, size = area, style = Stroke(grosor, cap = StrokeCap.Round))
        }
        Text(
            text = segundos.toString(),
            style = ContrasenoraTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = if (urgente) c.dangerFg else c.textPrimary,
        )
    }
}

/**
 * Estado vacío como invitación: el isotipo, un título en Young Serif, una frase y un único botón.
 */
@Composable
fun EstadoVacio(
    titulo: String,
    mensaje: String,
    modifier: Modifier = Modifier,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.s8)) {
        Isotipo(Modifier.size(72.dp))
        Text(
            titulo,
            style = t.display2,
            color = c.textPrimary,
            modifier = Modifier.padding(top = Spacing.s6, bottom = Spacing.s3).semantics { heading() },
        )
        Text(mensaje, style = t.bodyLarge, color = c.textSecondary)
        if (accion != null && onAccion != null) {
            BotonPrimario(accion, onAccion, modifier = Modifier.padding(top = Spacing.s6), icono = R.drawable.ic_anadir)
        }
    }
}
