package io.github.jls97.boveda.ui.components

import io.github.jls97.boveda.ui.theme.Personalidad
import io.github.jls97.boveda.ui.theme.elige

/**
 * Cuándo se borra del portapapeles lo que se acaba de copiar, en un fragmento común a todos los
 * avisos de copia: «se borrará del portapapeles en N s o al bloquearse la bóveda». Es cierto con
 * cualquier ajuste de autobloqueo: el temporizador (y la alarma que lo respalda) borra el clip a
 * los N s y cualquier bloqueo, el de «Al salir de la app» incluido, lo borra antes; con una
 * pantalla del sistema anunciada (selector de archivos) salir no bloquea, así que el aviso no
 * promete el borrado «al salir» (R03-6).
 */
fun clipboardClearNotice(clearAfterSeconds: Int): String =
    "se borrará del portapapeles en $clearAfterSeconds s o al bloquearse la bóveda"

/** Lo que se copia y su género, para que el aviso concuerde: «Contraseña copiada», «Usuario copiado». */
private val femeninos = setOf("Contraseña", "Dirección")

/**
 * Aviso tras copiar [que] («Contraseña», «Usuario», «Dirección»). Dice siempre cuándo se borra
 * del portapapeles; la Contraseñora añade su comentario y la versión Sobria, no.
 */
fun copyNotice(que: String, clearAfterSeconds: Int, personalidad: Personalidad): String {
    val femenino = que in femeninos
    val copiado = if (femenino) "copiada" else "copiado"
    return personalidad.elige(
        "$que $copiado. ${if (femenino) "La" else "Lo"} borro del portapapeles en $clearAfterSeconds s " +
            "o al bloquearse la bóveda, que nos conocemos.",
        "$que $copiado. ${clipboardClearNotice(clearAfterSeconds).replaceFirstChar { it.uppercase() }}.",
    )
}

/** Aviso tras copiar un código 2FA: cuánto le queda al código y cuándo se borra del portapapeles. */
fun codeCopyNotice(codeSecondsLeft: Int, clearAfterSeconds: Int, personalidad: Personalidad): String =
    personalidad.elige(
        "Código copiado. Corre, que caduca en $codeSecondsLeft s; lo borro del portapapeles en " +
            "$clearAfterSeconds s o al bloquearse la bóveda.",
        "Código copiado: cambia en $codeSecondsLeft s y ${clipboardClearNotice(clearAfterSeconds)}.",
    )
