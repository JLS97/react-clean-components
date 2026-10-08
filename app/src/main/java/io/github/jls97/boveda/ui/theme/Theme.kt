package io.github.jls97.boveda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContrasenoraColors = staticCompositionLocalOf { LightContrasenoraColors }
val LocalContrasenoraTypography = staticCompositionLocalOf { ContrasenoraTypography() }

/**
 * Tema raíz. Envuelve toda la app en MainActivity.setContent { ContrasenoraTheme { … } }.
 * El color dinámico (Material You) está desactivado a propósito: la marca manda.
 *
 * @param darkTheme sigue al sistema por defecto; permite forzarlo desde Ajustes.
 * @param personalidad registro de voz guardado por el usuario.
 */
@Composable
fun ContrasenoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    personalidad: Personalidad = Personalidad.Contrasenora,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkContrasenoraColors else LightContrasenoraColors
    val typography = ContrasenoraTypography()
    CompositionLocalProvider(
        LocalContrasenoraColors provides colors,
        LocalContrasenoraTypography provides typography,
        LocalPersonalidad provides personalidad,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            shapes = ContrasenoraMaterialShapes,
            content = content,
        )
    }
}

/** Acceso a los tokens: ContrasenoraTheme.colors.brandPrimary, ContrasenoraTheme.type.code… */
object ContrasenoraTheme {
    val colors: ContrasenoraColors
        @Composable @ReadOnlyComposable get() = LocalContrasenoraColors.current
    val type: ContrasenoraTypography
        @Composable @ReadOnlyComposable get() = LocalContrasenoraTypography.current
    val personalidad: Personalidad
        @Composable @ReadOnlyComposable get() = LocalPersonalidad.current
}

/**
 * Correspondencia con Material 3 (requiere material3 ≥ 1.2 por los roles surfaceContainer*).
 * surfaceTint = superficie para anular el tinte tonal de la elevación de Material.
 */
fun ContrasenoraColors.toMaterialColorScheme(): ColorScheme =
    if (isDark) darkColorScheme(
        primary = brandPrimary,
        onPrimary = brandOnPrimary,
        primaryContainer = brandPrimarySubtle,
        onPrimaryContainer = brandOnPrimarySubtle,
        inversePrimary = if (isDark) Palette.Ciruela700 else Palette.Rulo300,
        secondary = accentDefault,
        onSecondary = accentOn,
        secondaryContainer = accentSubtle,
        onSecondaryContainer = textPrimary,
        tertiary = brassDefault,
        onTertiary = textPrimary,
        tertiaryContainer = bgSunken,
        onTertiaryContainer = brassText,
        background = bgCanvas,
        onBackground = textPrimary,
        surface = bgSurface,
        onSurface = textPrimary,
        surfaceVariant = bgSunken,
        onSurfaceVariant = textSecondary,
        surfaceTint = bgSurface,
        inverseSurface = bgInverse,
        inverseOnSurface = textInverse,
        error = dangerFg,
        onError = dangerOnSolid,
        errorContainer = dangerBg,
        onErrorContainer = dangerFg,
        outline = borderStrong,
        outlineVariant = borderSubtle,
        scrim = bgScrim,
        surfaceBright = bgRaised,
        surfaceContainer = bgSurface,
        surfaceContainerHigh = bgRaised,
        surfaceContainerHighest = bgRaised,
        surfaceContainerLow = bgCanvas,
        surfaceContainerLowest = bgCanvas,
        surfaceDim = bgSunken,
    ) else lightColorScheme(
        primary = brandPrimary,
        onPrimary = brandOnPrimary,
        primaryContainer = brandPrimarySubtle,
        onPrimaryContainer = brandOnPrimarySubtle,
        inversePrimary = if (isDark) Palette.Ciruela700 else Palette.Rulo300,
        secondary = accentDefault,
        onSecondary = accentOn,
        secondaryContainer = accentSubtle,
        onSecondaryContainer = textPrimary,
        tertiary = brassDefault,
        onTertiary = textPrimary,
        tertiaryContainer = bgSunken,
        onTertiaryContainer = brassText,
        background = bgCanvas,
        onBackground = textPrimary,
        surface = bgSurface,
        onSurface = textPrimary,
        surfaceVariant = bgSunken,
        onSurfaceVariant = textSecondary,
        surfaceTint = bgSurface,
        inverseSurface = bgInverse,
        inverseOnSurface = textInverse,
        error = dangerFg,
        onError = dangerOnSolid,
        errorContainer = dangerBg,
        onErrorContainer = dangerFg,
        outline = borderStrong,
        outlineVariant = borderSubtle,
        scrim = bgScrim,
        surfaceBright = bgRaised,
        surfaceContainer = bgSurface,
        surfaceContainerHigh = bgRaised,
        surfaceContainerHighest = bgRaised,
        surfaceContainerLow = bgCanvas,
        surfaceContainerLowest = bgCanvas,
        surfaceDim = bgSunken,
    )
