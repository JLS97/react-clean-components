package io.github.jls97.boveda.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object Motion {
    const val INSTANT = 80
    const val FAST = 140          // pulsaciones, toggles
    const val BASE = 220          // revelar contraseña, abrir paneles, estampar sello
    const val SLOW = 360          // hojas modales, cambio de pantalla
    const val LOCK_SHAKE = 420    // sacudida del campo al fallar la contraseña maestra

    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(0.3f, 0f, 0f, 1f)
    val Exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}

/**
 * true si el usuario ha desactivado las animaciones (Ajustes > Accesibilidad > Quitar animaciones).
 * Con reducción activa: sin sacudida, sin estampado, el anillo TOTP salta segundo a segundo.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        // Sin ajustes legibles (vistas previas, capturas) se animan como siempre.
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
