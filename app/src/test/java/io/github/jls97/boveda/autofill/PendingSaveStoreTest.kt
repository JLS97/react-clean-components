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
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private val store = PendingSaveStore({ now }) { delay, action -> scheduled += delay to action }

    private fun save(username: String) = PendingSave(AutofillTarget("com.example.app", null), username, "secreto".toCharArray())

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
    fun `la caducidad también se programa sin esperar a la siguiente llamada`() {
        val token = store.put(save("ana"))
        assertEquals(1, scheduled.size)
        assertEquals(PendingSaveStore.MAX_AGE_MS, scheduled.single().first)
        assertNotNull(store.get(token))
        scheduled.single().second()
        assertNull(store.get(token))
    }

    @Test
    fun `lo que sale del almacén queda borrado de memoria`() {
        val removed = save("ana")
        val expired = save("luis")
        val cleared = save("eva")
        val chars = "secreto".toCharArray()
        val displaced = PendingSave(AutofillTarget("com.example.app", null), "pepe", chars)
        assertEquals("secreto", removed.password)
        assertEquals(false, removed.wiped)

        store.remove(store.put(removed))
        assertEquals(true, removed.wiped)
        assertEquals("", removed.password)

        store.put(expired)
        now += PendingSaveStore.MAX_AGE_MS + 1
        store.put(save("otra"))
        assertEquals(true, expired.wiped)

        val displacedToken = store.put(displaced)
        repeat(PendingSaveStore.MAX_SIZE) { store.put(save("u$it")) }
        assertNull(store.get(displacedToken))
        assertEquals(true, displaced.wiped)
        assertEquals(true, chars.all { it == '\u0000' })

        val clearedToken = store.put(cleared)
        store.clear()
        assertNull(store.get(clearedToken))
        assertEquals(true, cleared.wiped)
        assertEquals("", cleared.password)
    }

    @Test
    fun `vaciar deja todas inaccesibles`() {
        val a = store.put(save("a"))
        val b = store.put(save("b"))
        store.clear()
        assertNull(store.get(a))
        assertNull(store.get(b))
        assertNotNull(store.get(store.put(save("c"))))
    }

    @Test
    fun `toString no revela las credenciales`() {
        val text = save("ana").toString()
        assertEquals(false, text.contains("ana"))
        assertEquals(false, text.contains("secreto"))
    }
}
