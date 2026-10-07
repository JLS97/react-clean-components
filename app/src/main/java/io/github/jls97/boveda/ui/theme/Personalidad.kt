package io.github.jls97.boveda.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource

/**
 * Registro de voz de la app. Se elige en Ajustes > Personalidad y se guarda en
 * [io.github.jls97.boveda.data.Apariencia] (SharedPreferences, fuera de la bóveda).
 * Contrasenora: con humor (por defecto). Sobria: la misma información, sin chistes.
 */
enum class Personalidad { Contrasenora, Sobria }

val LocalPersonalidad = staticCompositionLocalOf { Personalidad.Contrasenora }

/**
 * Devuelve el texto en el registro activo. Convención de strings.xml:
 * "clave" = Contraseñora, "clave_sobrio" = Sobria.
 *
 *   vozString(R.string.item_password_copied, R.string.item_password_copied_sobrio, 30)
 */
@Composable
fun vozString(@StringRes contrasenora: Int, @StringRes sobrio: Int, vararg args: Any): String {
    val id = if (LocalPersonalidad.current == Personalidad.Sobria) sobrio else contrasenora
    return if (args.isEmpty()) stringResource(id) else stringResource(id, *args)
}

/**
 * Texto escrito en Kotlin en el registro activo. El humor va solo donde la guía lo permite (intentos
 * fallidos, estados vacíos, confirmaciones de éxito); los avisos importantes son iguales en ambos.
 *
 *   voz("Esa no es. Revisa mayúsculas y vuelve a intentarlo.", "Contraseña incorrecta. Revisa mayúsculas.")
 */
@Composable
@ReadOnlyComposable
fun voz(contrasenora: String, sobria: String): String =
    if (LocalPersonalidad.current == Personalidad.Sobria) sobria else contrasenora

/** Lo mismo que [voz] fuera de la composición (ViewModels, mensajes). */
fun Personalidad.elige(contrasenora: String, sobria: String): String =
    if (this == Personalidad.Sobria) sobria else contrasenora
