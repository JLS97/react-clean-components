package io.github.jls97.boveda.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.core.generator.PasswordStrength
import io.github.jls97.boveda.core.generator.StrengthLevel
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.voz

/** Nivel del medidor, de 0 (muy débil) a 4 (muy fuerte). */
fun StrengthLevel.nivel(): Int = when (this) {
    StrengthLevel.VERY_WEAK -> 0
    StrengthLevel.WEAK -> 1
    StrengthLevel.FAIR -> 2
    StrengthLevel.STRONG -> 3
    StrengthLevel.VERY_STRONG -> 4
}

/** El nombre del nivel, sin más: «muy débil», «fuerte»… (para frases como «≈ 80 bits · fuerte»). */
fun strengthLabel(level: StrengthLevel): String = when (level) {
    StrengthLevel.VERY_WEAK -> "muy débil"
    StrengthLevel.WEAK -> "débil"
    StrengthLevel.FAIR -> "aceptable"
    StrengthLevel.STRONG -> "fuerte"
    StrengthLevel.VERY_STRONG -> "muy fuerte"
}

/** La etiqueta del medidor en el registro elegido: con su comentario o a secas. */
@Composable
fun etiquetaFuerza(level: StrengthLevel): String = when (level) {
    StrengthLevel.VERY_WEAK -> voz("Muy débil: esto lo adivina hasta el gato.", "Muy débil")
    StrengthLevel.WEAK -> voz("Débil: aguanta lo que un merengue en la puerta de un colegio.", "Débil")
    StrengthLevel.FAIR -> voz("Aceptable: pasable, pero yo le añadiría unos caracteres.", "Aceptable")
    StrengthLevel.STRONG -> voz("Fuerte: así me gusta.", "Fuerte")
    StrengthLevel.VERY_STRONG -> voz("Muy fuerte: ni el hacker más pesado. Me lo llevo a la tumba.", "Muy fuerte")
}

/**
 * Medidor de fuerza: cinco segmentos con los colores strength.0–4 y la etiqueta del nivel en
 * texto, así que nunca depende solo del color. Los segmentos cambian de color con suavidad.
 */
@Composable
fun MedidorFuerza(level: StrengthLevel, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    val n = level.nivel()
    val etiqueta = etiquetaFuerza(level)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Fuerza: $etiqueta" }) {
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(5) { i ->
                val color by animateColorAsState(
                    targetValue = if (i <= n) c.strength[n] else c.strengthTrack,
                    animationSpec = tween(Motion.BASE, easing = Motion.Standard),
                    label = "segmento $i",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(color, RoundedCornerShape(3.dp)),
                )
            }
        }
        Text(
            etiqueta,
            style = ContrasenoraTheme.type.small,
            color = c.textSecondary,
            modifier = Modifier.padding(top = Spacing.s2).clearAndSetSemantics { },
        )
    }
}

/** El medidor para lo que se está escribiendo; no se muestra con el campo vacío. */
@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return
    MedidorFuerza(PasswordStrength.level(PasswordStrength.estimateBits(password)), modifier)
}
