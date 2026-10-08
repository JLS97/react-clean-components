package io.github.jls97.boveda.core.autofill

import io.github.jls97.boveda.core.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decisiones puras del guardado por autorrelleno: contraseña nueva frente a actual, preselección y resumen. */
class SaveCaptureTest {

    private fun entry(id: String, username: String, password: String, title: String = "Banco") = VaultEntry(
        id = id,
        title = title,
        username = username,
        password = password,
        url = "https://banco.es",
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `formulario de login guarda la contraseña del campo PASSWORD`() {
        assertEquals("actual", SaveCapture.choosePassword("actual", emptyList()))
    }

    @Test
    fun `cambio de contraseña guarda la nueva y no la actual`() {
        assertEquals("nueva", SaveCapture.choosePassword("actual", listOf("nueva", "nueva")))
        assertEquals("nueva", SaveCapture.choosePassword("actual", listOf("nueva")))
    }

    @Test
    fun `dos contraseñas nuevas distintas no se guardan`() {
        assertNull(SaveCapture.choosePassword("actual", listOf("nueva", "nueva2")))
        assertNull(SaveCapture.choosePassword(null, listOf("nueva", "otra")))
    }

    @Test
    fun `confirmación vacía o sin leer no invalida la nueva`() {
        assertEquals("nueva", SaveCapture.choosePassword("actual", listOf("nueva", "")))
        assertEquals("nueva", SaveCapture.choosePassword(null, listOf(null, "nueva")))
    }

    @Test
    fun `campos nuevos vacíos recurren a la actual y sin nada no hay contraseña`() {
        assertEquals("actual", SaveCapture.choosePassword("actual", listOf("", null)))
        assertNull(SaveCapture.choosePassword("", listOf("")))
        assertNull(SaveCapture.choosePassword(null, emptyList()))
    }

    @Test
    fun `solo se preselecciona la entrada cuyo usuario coincide`() {
        val ana = entry("1", "ana@banco.es", "p1")
        val luis = entry("2", "luis", "p2")
        assertEquals(ana, SaveCapture.preselect(listOf(luis, ana), " Ana@Banco.es "))
        assertNull(SaveCapture.preselect(listOf(luis, ana), "otro"))
    }

    @Test
    fun `en la web solo se preselecciona la entrada anclada al mismo host, no a un dominio padre`() {
        val ana = entry("1", "ana@banco.es", "p1")
        // La entrada de banco.es coincide con aviso.banco.es (subdominio abandonado o comprometido)
        // pero no se ofrece sobrescribirla por defecto.
        assertNull(SaveCapture.preselect(listOf(ana), "ana@banco.es", "aviso.banco.es"))
        assertEquals(ana, SaveCapture.preselect(listOf(ana), "ana@banco.es", "banco.es"))
        // Un vínculo web: al host exacto también vale.
        val linked = entry("2", "ana@banco.es", "p2").copy(url = "", autofillTargets = listOf("web:aviso.banco.es"))
        assertEquals(linked, SaveCapture.preselect(listOf(ana, linked), "ana@banco.es", "aviso.banco.es"))
        // Sin host (una app) basta con el usuario, como antes.
        assertEquals(ana, SaveCapture.preselect(listOf(ana), "ana@banco.es", null))
    }

    @Test
    fun `sin usuario tecleado no se preselecciona nada, ni una entrada sin usuario`() {
        assertNull(SaveCapture.preselect(listOf(entry("1", "", "p1"), entry("2", "ana", "p2")), ""))
        assertNull(SaveCapture.preselect(emptyList(), "ana"))
    }

    @Test
    fun `una contraseña idéntica a la almacenada no se ofrece guardar`() {
        val ana = entry("1", "ana", "misma")
        assertEquals(ana, SaveCapture.alreadyStored(listOf(ana), "ana", "misma"))
        assertEquals(ana, SaveCapture.alreadyStored(listOf(ana), "", "misma"))
        assertNull(SaveCapture.alreadyStored(listOf(ana), "ana", "distinta"))
        assertNull(SaveCapture.alreadyStored(listOf(ana), "luis", "misma"))
    }

    @Test
    fun `al actualizar se conserva el usuario si el formulario no aportó uno`() {
        val ana = entry("1", "ana", "p1")
        assertEquals("ana", SaveCapture.mergedUsername(ana, ""))
        assertEquals("ana", SaveCapture.mergedUsername(ana, "   "))
        assertEquals("nuevo", SaveCapture.mergedUsername(ana, " nuevo "))
        assertEquals("", SaveCapture.mergedUsername(null, ""))
    }

    @Test
    fun `el resumen describe el cambio de usuario y de contraseña`() {
        val ana = entry("1", "ana", "antigua12")
        assertEquals(
            "Usuario: sin cambios · Contraseña: nueva de 5 caracteres, antes 9",
            SaveCapture.changeSummary(ana, "", "nueva"),
        )
        assertEquals(
            "Usuario: «luis», antes «ana» · Contraseña: sin cambios",
            SaveCapture.changeSummary(ana, "luis", "antigua12"),
        )
        assertEquals(
            "Usuario: «luis», antes vacío · Contraseña: nueva de 5 caracteres, antes 9",
            SaveCapture.changeSummary(entry("2", "", "antigua12"), "luis", "nueva"),
        )
    }
}
