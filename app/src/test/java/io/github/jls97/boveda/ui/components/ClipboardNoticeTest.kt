package io.github.jls97.boveda.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** El aviso común de borrado del portapapeles que comparten los dos ViewModels (R03-6). */
class ClipboardNoticeTest {

    @Test
    fun noticeNamesTheTimeoutAndTheLock() {
        assertEquals("se borrará del portapapeles en 30 s o al bloquearse la bóveda", clipboardClearNotice(30))
        assertEquals("se borrará del portapapeles en 60 s o al bloquearse la bóveda", clipboardClearNotice(60))
    }

    @Test
    fun noticeNeverPromisesAClearOnLeavingTheApp() {
        // Con «Al salir de la app» una pantalla del sistema anunciada no bloquea al salir, así que
        // el texto no depende del ajuste de autobloqueo.
        assertEquals(false, clipboardClearNotice(30).contains("al salir"))
    }

    @Test
    fun noticeFitsAfterTheCopyLabelAndInsideTheOtpMessage() {
        assertEquals(
            "Contraseña copiado. Se borrará del portapapeles en 45 s o al bloquearse la bóveda.",
            "Contraseña copiado. ${clipboardClearNotice(45).replaceFirstChar { it.uppercase() }}.",
        )
        assertEquals(
            "Código copiado: cambia en 12 s y se borrará del portapapeles en 45 s o al bloquearse la bóveda.",
            "Código copiado: cambia en 12 s y ${clipboardClearNotice(45)}.",
        )
    }
}
