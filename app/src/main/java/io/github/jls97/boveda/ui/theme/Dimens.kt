package io.github.jls97.boveda.ui.theme

import androidx.compose.ui.unit.dp

/** Espaciado en base 4. Margen lateral de pantalla: s4 (16 dp), s5 en pantallas ≥ 400 dp. */
object Spacing {
    val s0_5 = 2.dp
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s16 = 64.dp
    val s20 = 80.dp
}

object Sizes {
    /** Mínimo para todo lo pulsable, aunque el icono sea más pequeño. */
    val touchTarget = 48.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val itemIcon = 40.dp
    val totpRing = 36.dp
    val totpRingStroke = 4.dp
    val buttonHeight = 48.dp
    val buttonHeightCompact = 40.dp
    val inputHeight = 52.dp
    val listItemHeight = 64.dp
    val inputBorder = 1.5.dp
    val contentMaxWidth = 720.dp
}

/** Elevación (sombras). En tema oscuro la jerarquía se marca sobre todo con bgRaised. */
object Elevation {
    val none = 0.dp
    val sm = 1.dp
    val md = 4.dp
    val lg = 12.dp
}

/** Comportamientos ligados a la marca y a la seguridad. */
object Comportamiento {
    const val PORTAPAPELES_BORRADO_SEGUNDOS = 30
    const val TOTP_URGENTE_SEGUNDOS = 5
    const val CONTRASENA_REVELADA_SEGUNDOS = 30
    const val SNACKBAR_MS = 4000L
}
