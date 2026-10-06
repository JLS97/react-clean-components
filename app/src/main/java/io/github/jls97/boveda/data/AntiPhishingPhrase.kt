package io.github.jls97.boveda.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Longitud mínima de la frase antiphishing, en caracteres (ya sin espacios en los extremos). */
const val ANTI_PHISHING_MIN_LENGTH = 3

/** Longitud máxima de la frase antiphishing, en caracteres (ya sin espacios en los extremos). */
const val ANTI_PHISHING_MAX_LENGTH = 40

/**
 * Por qué [phrase] no vale como frase antiphishing, o null si vale. Se compara ya recortada: los
 * espacios de los extremos no cuentan y no se guardan.
 */
fun antiPhishingPhraseProblem(phrase: String): String? {
    val trimmed = phrase.trim()
    return when {
        trimmed.length < ANTI_PHISHING_MIN_LENGTH -> "Usa al menos $ANTI_PHISHING_MIN_LENGTH caracteres."
        trimmed.length > ANTI_PHISHING_MAX_LENGTH -> "Usa como mucho $ANTI_PHISHING_MAX_LENGTH caracteres."
        trimmed.lines().size > 1 -> "Escríbela en una sola línea."
        else -> null
    }
}

/**
 * Frase personal que la pantalla de desbloqueo muestra antes de pedir la contraseña maestra (M-04).
 * Una app que imite esa pantalla (por ejemplo tras un chip de autorrelleno falso) no la conoce, así
 * que «si no ves tu frase, no escribas la contraseña». Vive en SharedPreferences, fuera de la bóveda
 * cifrada, porque hace falta antes de abrirla; no es un secreto que proteja nada por sí misma, y el
 * directorio privado de la app no es legible por otras apps.
 */
class AntiPhishingPhrase(context: Context) {
    private val prefs = context.getSharedPreferences("anti_phishing", Context.MODE_PRIVATE)
    private val _phrase = MutableStateFlow(read())

    /** La frase actual, o null si nunca se ha elegido (bóvedas creadas antes de existir esta opción). */
    val phrase: StateFlow<String?> = _phrase.asStateFlow()

    /** Guarda [phrase] recortada. Debe haber pasado antes por [antiPhishingPhraseProblem]. */
    fun save(phrase: String) {
        prefs.edit().putString(KEY_PHRASE, phrase.trim()).apply()
        _phrase.value = read()
    }

    private fun read(): String? = prefs.getString(KEY_PHRASE, null)?.takeIf { it.isNotBlank() }

    private companion object {
        const val KEY_PHRASE = "phrase"
    }
}
