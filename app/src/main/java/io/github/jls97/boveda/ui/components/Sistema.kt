package io.github.jls97.boveda.ui.components

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Pone los iconos de las barras del sistema (hora, batería, navegación) del color que pide el tema
 * de la app, que en Ajustes → Apariencia puede no coincidir con el del teléfono: con la app en
 * oscuro y el teléfono en claro, los iconos oscuros quedarían invisibles sobre el fondo.
 */
@Composable
fun BarrasDelSistema(oscuro: Boolean) {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    DisposableEffect(activity, oscuro) {
        val estilo = if (oscuro) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
        onDispose { }
    }
}
