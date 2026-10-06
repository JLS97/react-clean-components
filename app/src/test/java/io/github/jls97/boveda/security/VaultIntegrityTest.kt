package io.github.jls97.boveda.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rollback record with a fake store: a file put back is told apart from the last one written. */
class VaultIntegrityTest {

    private class FakeStore : IntegrityStore {
        var record: IntegrityRecord? = null

        override fun load(): IntegrityRecord? = record

        override fun save(record: IntegrityRecord) {
            this.record = record
        }
    }

    private val store = FakeStore()
    private val integrity = VaultIntegrity(store)

    private val first = "vault generation 1".toByteArray()
    private val second = "vault generation 2".toByteArray()

    @Test
    fun withoutARecordEveryFileIsAccepted() {
        assertTrue(integrity.isLatest(first))
        assertTrue(integrity.isLatest(ByteArray(0)))
    }

    @Test
    fun theLastFileWrittenIsTheLatest() {
        integrity.recordWrite(first)
        assertTrue(integrity.isLatest(first))
        assertTrue(integrity.isLatest(first.copyOf())) // same bytes, another array
        assertFalse(integrity.isLatest(second))
    }

    @Test
    fun anOlderFilePutBackIsDetected() {
        integrity.recordWrite(first)
        integrity.recordWrite(second)
        assertTrue(integrity.isLatest(second))
        assertFalse("A rolled-back vault.bin must be noticed", integrity.isLatest(first))
        assertFalse(integrity.isLatest(second + byteArrayOf(0)))
    }

    @Test
    fun theRecordHoldsTheHashAndCountsTheWrites() {
        integrity.recordWrite(first)
        val one = store.record
        assertNotNull(one)
        assertEquals(1L, one!!.generation)
        assertArrayEquals(VaultIntegrity.sha256(first), one.sha256)
        integrity.recordWrite(second)
        assertEquals(2L, store.record!!.generation)
        assertArrayEquals(VaultIntegrity.sha256(second), store.record!!.sha256)
        assertFalse(one.sha256.contentEquals(store.record!!.sha256))
    }
}
