package io.github.jls97.boveda.ui.components

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * false en la pantalla que se va durante una transición: sigue a la vista un momento, pero ya no
 * debe atender al gesto de atrás (lo atiende la que llega).
 */
val LocalPantallaActiva = compositionLocalOf { true }

/**
 * Tapa [contenido] mientras no esté [activa]: los toques no llegan a una pantalla que se está yendo.
 */
@Composable
fun SinToquesSiSeVa(activa: Boolean, contenido: @Composable () -> Unit) {
    Box {
        CompositionLocalProvider(LocalPantallaActiva provides activa) { contenido() }
        if (!activa) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
            )
        }
    }
}
