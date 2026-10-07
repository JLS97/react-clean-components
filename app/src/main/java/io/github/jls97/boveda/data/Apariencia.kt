package io.github.jls97.boveda.data

import android.content.Context
import io.github.jls97.boveda.ui.theme.Personalidad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tema de la interfaz: el del sistema o uno fijo elegido en Ajustes. */
enum class Tema { Sistema, Claro, Oscuro }

/** Preferencias de apariencia: registro de voz y tema. */
data class AjustesApariencia(
    val personalidad: Personalidad = Personalidad.Contrasenora,
    val tema: Tema = Tema.Sistema,
) {
    /** Si la interfaz va en oscuro, dado lo que dice el sistema. */
    fun oscuro(sistemaOscuro: Boolean): Boolean = when (tema) {
        Tema.Sistema -> sistemaOscuro
        Tema.Claro -> false
        Tema.Oscuro -> true
    }
}

/**
 * Apariencia de la app (personalidad Contraseñora o Sobria y tema). Vive en SharedPreferences, fuera
 * de la bóveda cifrada, porque la pantalla de desbloqueo ya habla con esa voz y en ese tema antes de
 * abrirla. No es un secreto: solo dice cómo se pinta y cómo se escribe la interfaz.
 */
class Apariencia(context: Context) {
    private val prefs = context.getSharedPreferences("apariencia", Context.MODE_PRIVATE)
    private val _ajustes = MutableStateFlow(leer())
    val ajustes: StateFlow<AjustesApariencia> = _ajustes.asStateFlow()

    fun cambiarPersonalidad(personalidad: Personalidad) {
        prefs.edit().putString(KEY_PERSONALIDAD, personalidad.name).apply()
        _ajustes.value = leer()
    }

    fun cambiarTema(tema: Tema) {
        prefs.edit().putString(KEY_TEMA, tema.name).apply()
        _ajustes.value = leer()
    }

    private fun leer() = AjustesApariencia(
        personalidad = enumOrDefault(prefs.getString(KEY_PERSONALIDAD, null), Personalidad.Contrasenora),
        tema = enumOrDefault(prefs.getString(KEY_TEMA, null), Tema.Sistema),
    )

    private companion object {
        const val KEY_PERSONALIDAD = "personalidad"
        const val KEY_TEMA = "tema"

        inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
