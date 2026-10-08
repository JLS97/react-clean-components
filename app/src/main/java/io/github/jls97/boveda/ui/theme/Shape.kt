package io.github.jls97.boveda.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radios con jerarquía: cuanto más grande el contenedor, más redondo (como el cuerpo del candado).
 * xs chips · sm botones e inputs · md tarjetas y avisos · lg hojas y diálogos · xl contenedores grandes.
 */
object ContrasenoraShapes {
    val xs = RoundedCornerShape(6.dp)
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(24.dp)
    val xl = RoundedCornerShape(32.dp)
    val full = RoundedCornerShape(percent = 50)
    /** Bordes del sello de goma (exterior e interior). */
    val selloExterior = RoundedCornerShape(8.dp)
    val selloInterior = RoundedCornerShape(5.dp)
}

val ContrasenoraMaterialShapes = Shapes(
    extraSmall = ContrasenoraShapes.xs,
    small = ContrasenoraShapes.sm,
    medium = ContrasenoraShapes.md,
    large = ContrasenoraShapes.lg,
    extraLarge = ContrasenoraShapes.xl,
)
