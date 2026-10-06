package io.github.jls97.boveda.autofill

import io.github.jls97.boveda.core.autofill.AutofillTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Credenciales pendientes de guardar: límite de tamaño y caducidad con reloj inyectado. */
class PendingSaveStoreTest {

    private var now = 1_000_000L
    private val store = PendingSaveStore { now }

    private fun save(username: String) = PendingSave(AutofillTarget("com.example.app", null), username, "secreto")

    @Test
    fun `se recupera mientras no caduque y leer no la elimina`() {
        val token = store.put(save("ana"))
        now += PendingSaveStore.MAX_AGE_MS
        val first = store.get(token)
        assertEquals("ana", first?.username)
        assertSame(first, store.get(token))
    }

    @Test
    fun `caduca a los cinco minutos al leer`() {
        val token = store.put(save("ana"))
        now += PendingSaveStore.MAX_AGE_MS + 1
        assertNull(store.get(token))
    }

    @Test
    fun `caduca a los cinco minutos al guardar otra`() {
        val old = store.put(save("ana"))
        now += PendingSaveStore.MAX_AGE_MS + 1
        val fresh = store.put(save("luis"))
        now += 1
        // La antigua ya no está aunque la nueva no haya hecho ninguna lectura que la pode.
        assertNull(store.get(old))
        assertNotNull(store.get(fresh))
    }

    @Test
    fun `como mucho ocho pendientes y se descarta la más antigua`() {
        val tokens = (1..PendingSaveStore.MAX_SIZE).map { store.put(save("u$it")) }
        tokens.forEach { assertNotNull(store.get(it)) }
        val extra = store.put(save("extra"))
        assertNull(store.get(tokens.first()))
        tokens.drop(1).forEach { assertNotNull(store.get(it)) }
        assertEquals("extra", store.get(extra)?.username)
    }

    @Test
    fun `eliminar la deja inaccesible y los tokens son únicos`() {
        val a = store.put(save("a"))
        val b = store.put(save("b"))
        assertEquals(false, a == b)
        store.remove(a)
        assertNull(store.get(a))
        assertNotNull(store.get(b))
    }

    @Test
    fun `toString no revela las credenciales`() {
        val text = save("ana").toString()
        assertEquals(false, text.contains("ana"))
        assertEquals(false, text.contains("secreto"))
    }
}
