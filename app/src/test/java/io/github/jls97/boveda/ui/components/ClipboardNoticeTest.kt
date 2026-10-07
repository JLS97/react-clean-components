package io.github.jls97.boveda.ui.components

import io.github.jls97.boveda.ui.theme.Personalidad
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
    fun soberCopyNoticeAgreesInGenderAndNamesTheClear() {
        assertEquals(
            "Contraseña copiada. Se borrará del portapapeles en 45 s o al bloquearse la bóveda.",
            copyNotice("Contraseña", 45, Personalidad.Sobria),
        )
        assertEquals(
            "Usuario copiado. Se borrará del portapapeles en 45 s o al bloquearse la bóveda.",
            copyNotice("Usuario", 45, Personalidad.Sobria),
        )
        assertEquals(
            "Dirección copiada. Se borrará del portapapeles en 45 s o al bloquearse la bóveda.",
            copyNotice("Dirección", 45, Personalidad.Sobria),
        )
    }

    @Test
    fun contrasenoraCopyNoticeKeepsTheClearPromise() {
        val notice = copyNotice("Contraseña", 30, Personalidad.Contrasenora)
        assertEquals(true, notice.startsWith("Contraseña copiada. La borro del portapapeles en 30 s o al bloquearse la bóveda"))
        assertEquals("Usuario copiado. Lo borro", copyNotice("Usuario", 30, Personalidad.Contrasenora).substring(0, 25))
    }

    @Test
    fun codeCopyNoticeNamesBothTimes() {
        assertEquals(
            "Código copiado: cambia en 12 s y se borrará del portapapeles en 45 s o al bloquearse la bóveda.",
            codeCopyNotice(12, 45, Personalidad.Sobria),
        )
        val withHumor = codeCopyNotice(12, 45, Personalidad.Contrasenora)
        assertEquals(true, withHumor.contains("caduca en 12 s") && withHumor.contains("45 s o al bloquearse la bóveda"))
        assertEquals(false, withHumor.contains("al salir"))
    }
}
