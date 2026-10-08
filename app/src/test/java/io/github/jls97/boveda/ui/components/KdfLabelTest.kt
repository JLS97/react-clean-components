package io.github.jls97.boveda.ui.components

import io.github.jls97.boveda.core.crypto.KdfParams
import org.junit.Assert.assertEquals
import org.junit.Test

/** La línea de Ajustes con los parámetros Argon2id de la bóveda (I-09). */
class KdfLabelTest {

    @Test
    fun defaultCostsReadAsTheDocumentedLine() {
        assertEquals("Argon2id · 64 MiB · 3 pasadas · 4 carriles", kdfLabel(KdfParams.DEFAULT))
    }

    @Test
    fun singularsAndSubMebibyteMemoryAreSpelledOut() {
        assertEquals("Argon2id · 64 KiB · 1 pasada · 1 carril", kdfLabel(KdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)))
        assertEquals("Argon2id · 256 MiB · 16 pasadas · 16 carriles", kdfLabel(KdfParams(KdfParams.MAX_MEMORY_KIB, 16, 16)))
    }
}
