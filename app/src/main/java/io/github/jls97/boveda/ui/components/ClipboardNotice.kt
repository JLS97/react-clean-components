package io.github.jls97.boveda.ui.components

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
