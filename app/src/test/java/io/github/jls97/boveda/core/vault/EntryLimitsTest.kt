package io.github.jls97.boveda.core.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los límites de lo que se puede teclear en una entrada y sus mensajes (R05-3). */
class EntryLimitsTest {

    private val base = VaultEntry(id = "a1", title = "Banco", createdAt = 1L, updatedAt = 1L)

    @Test
    fun utf8SizeCountsBytesLikeTheCodecWithoutCopyingTheValue() {
        for (text in listOf("", "abc", "ñ", "€", "😀", "p@ss\"word\\ñ€😀", "a".repeat(10_000) + "ñ")) {
            assertEquals(text, text.toByteArray(Charsets.UTF_8).size, EntryLimits.utf8Size(text))
        }
    }

    @Test
    fun typingLimitsStayWellBelowTheFormatCeilings() {
        assertEquals(mapOf("title" to 1_024, "username" to 1_024, "password" to 4_096, "url" to 2_048, "notes" to 65_536),
            mapOf(
                "title" to EntryLimits.TITLE_BYTES,
                "username" to EntryLimits.USERNAME_BYTES,
                "password" to EntryLimits.PASSWORD_BYTES,
                "url" to EntryLimits.URL_BYTES,
                "notes" to EntryLimits.NOTES_BYTES,
            ))
        assertTrue(EntryLimits.TITLE_BYTES < VaultCodec.MAX_TITLE_BYTES)
        assertTrue(EntryLimits.USERNAME_BYTES < VaultCodec.MAX_USERNAME_BYTES)
        assertTrue(EntryLimits.PASSWORD_BYTES < VaultCodec.MAX_PASSWORD_BYTES)
        assertTrue(EntryLimits.URL_BYTES < VaultCodec.MAX_URL_BYTES)
        assertTrue(EntryLimits.NOTES_BYTES < VaultCodec.MAX_NOTES_BYTES)
    }

    @Test
    fun aFieldAtItsLimitFitsAndOneByteMoreDoesNot() {
        assertNull(EntryLimits.titleError("t".repeat(EntryLimits.TITLE_BYTES)))
        assertEquals("El nombre supera 1 KB; recórtalo.", EntryLimits.titleError("t".repeat(EntryLimits.TITLE_BYTES + 1)))
        assertNull(EntryLimits.usernameError("u".repeat(EntryLimits.USERNAME_BYTES)))
        assertEquals("El usuario supera 1 KB; recórtalo.", EntryLimits.usernameError("u".repeat(EntryLimits.USERNAME_BYTES + 1)))
        assertNull(EntryLimits.passwordError("p".repeat(EntryLimits.PASSWORD_BYTES)))
        assertEquals("La contraseña supera 4 KB; recórtala.", EntryLimits.passwordError("p".repeat(EntryLimits.PASSWORD_BYTES + 1)))
        assertNull(EntryLimits.urlError("u".repeat(EntryLimits.URL_BYTES)))
        assertEquals("La dirección supera 2 KB; recórtala.", EntryLimits.urlError("u".repeat(EntryLimits.URL_BYTES + 1)))
        assertNull(EntryLimits.notesError("n".repeat(EntryLimits.NOTES_BYTES)))
        assertEquals("Las notas superan 64 KB; recórtalas.", EntryLimits.notesError("n".repeat(EntryLimits.NOTES_BYTES + 1)))
    }

    @Test
    fun limitsAreInBytesSoMultiByteCharactersCountMore() {
        // 513 «ñ» are 513 chars but 1 026 bytes.
        assertFalse(EntryLimits.fits("ñ".repeat(EntryLimits.TITLE_BYTES / 2 + 1), EntryLimits.TITLE_BYTES))
        assertTrue(EntryLimits.fits("ñ".repeat(EntryLimits.TITLE_BYTES / 2), EntryLimits.TITLE_BYTES))
        assertEquals("Las notas superan 64 KB; recórtalas.", EntryLimits.notesError("😀".repeat(EntryLimits.NOTES_BYTES / 4 + 1)))
    }

    @Test
    fun oversizedFieldNamesTheFirstFieldOverItsLimitOrNothing() {
        assertNull(EntryLimits.oversizedField(base))
        assertNull(EntryLimits.oversizedField(base.copy(notes = "n".repeat(EntryLimits.NOTES_BYTES), password = "p".repeat(EntryLimits.PASSWORD_BYTES))))
        assertEquals("Las notas superan 64 KB; recórtalas.", EntryLimits.oversizedField(base.copy(notes = "n".repeat(70 * 1_024))))
        assertEquals("La contraseña supera 4 KB; recórtala.", EntryLimits.oversizedField(base.copy(password = "p".repeat(5_000))))
        assertEquals("La dirección supera 2 KB; recórtala.", EntryLimits.oversizedField(base.copy(url = "u".repeat(2_049))))
        // Several fields over: the first one in the form, so the person fixes them top to bottom.
        assertEquals(
            "El usuario supera 1 KB; recórtalo.",
            EntryLimits.oversizedField(base.copy(username = "u".repeat(1_025), notes = "n".repeat(70 * 1_024))),
        )
    }
}
