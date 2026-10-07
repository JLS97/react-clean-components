package io.github.jls97.boveda.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.jls97.boveda.R

/** Young Serif: solo títulos grandes, marca, lápida y sellos. */
val YoungSerif = FontFamily(Font(R.font.young_serif_regular, FontWeight.Normal))

/** Atkinson Hyperlegible Next: interfaz y texto. Distingue I/l/1 y O/0. */
val AtkinsonNext = FontFamily(
    Font(R.font.atkinson_next_regular, FontWeight.Normal),
    Font(R.font.atkinson_next_medium, FontWeight.Medium),
    Font(R.font.atkinson_next_bold, FontWeight.Bold),
)

/** Atkinson Hyperlegible Mono: códigos 2FA y contraseñas reveladas. */
val AtkinsonMono = FontFamily(
    Font(R.font.atkinson_mono_regular, FontWeight.Normal),
    Font(R.font.atkinson_mono_medium, FontWeight.Medium),
)

/** Estilos de texto de la marca (en sp para respetar el tamaño de fuente del sistema). */
@Immutable
data class ContrasenoraTypography(
    val display1: TextStyle = TextStyle(fontFamily = YoungSerif, fontSize = 48.sp, lineHeight = 55.sp, letterSpacing = (-0.01).em),
    val display2: TextStyle = TextStyle(fontFamily = YoungSerif, fontSize = 36.sp, lineHeight = 41.sp, letterSpacing = (-0.01).em),
    val title1: TextStyle = TextStyle(fontFamily = YoungSerif, fontSize = 30.sp, lineHeight = 39.sp),
    val title2: TextStyle = TextStyle(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 31.sp),
    val title3: TextStyle = TextStyle(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    val bodyLarge: TextStyle = TextStyle(fontFamily = AtkinsonNext, fontSize = 18.sp, lineHeight = 27.sp),
    val body: TextStyle = TextStyle(fontFamily = AtkinsonNext, fontSize = 16.sp, lineHeight = 24.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp),
    val label: TextStyle = TextStyle(fontFamily = AtkinsonNext, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    val small: TextStyle = TextStyle(fontFamily = AtkinsonNext, fontSize = 14.sp, lineHeight = 21.sp),
    val caption: TextStyle = TextStyle(fontFamily = AtkinsonNext, fontSize = 12.sp, lineHeight = 18.sp),
    /** Código 2FA: agrupar 3 + 3 (o 4 + 4 si tiene 8 dígitos). Cifras tabulares. */
    val code: TextStyle = TextStyle(fontFamily = AtkinsonMono, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 37.sp, letterSpacing = 0.08.em, fontFeatureSettings = "tnum"),
    /** Contraseña revelada o generada. */
    val secret: TextStyle = TextStyle(fontFamily = AtkinsonMono, fontSize = 18.sp, lineHeight = 27.sp),
    /** Palabra del sello de goma de los avisos. Se muestra en mayúsculas. */
    val sello: TextStyle = TextStyle(fontFamily = YoungSerif, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.08.em),
)

/** Correspondencia con Material 3 para que los componentes de Material hereden la marca. */
fun ContrasenoraTypography.toMaterialTypography() = Typography(
    displayLarge = display1,
    displayMedium = display2,
    displaySmall = title1,
    headlineLarge = title1,
    headlineMedium = title2,
    headlineSmall = title3,
    titleLarge = title3,
    titleMedium = bodyStrong,
    titleSmall = label,
    bodyLarge = bodyLarge,
    bodyMedium = body,
    bodySmall = small,
    labelLarge = bodyStrong,   // texto de botones
    labelMedium = label,
    labelSmall = caption,
)
