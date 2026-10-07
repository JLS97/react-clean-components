package io.github.jls97.boveda.session

import io.github.jls97.boveda.core.crypto.KdfParams
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cuándo se reescribe la cabecera de una bóveda al desbloquearla con contraseña (I-09). */
class KdfUpgradePolicyTest {

    private val default = KdfParams.DEFAULT

    @Test
    fun aVaultWithTheDefaultCostsIsNotRewritten() {
        assertFalse(KdfUpgradePolicy.shouldUpgrade(default))
        assertFalse(KdfUpgradePolicy.shouldUpgrade(default.copy()))
    }

    @Test
    fun aVaultWithCheaperCostsIsRewritten() {
        assertTrue("32 MiB", KdfUpgradePolicy.shouldUpgrade(default.copy(memoryKiB = 32 * 1024)))
        assertTrue("fewer passes", KdfUpgradePolicy.shouldUpgrade(default.copy(iterations = default.iterations - 1)))
        assertTrue("test-grade costs", KdfUpgradePolicy.shouldUpgrade(KdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)))
        assertTrue("more memory does not buy back a pass", KdfUpgradePolicy.shouldUpgrade(default.copy(memoryKiB = 128 * 1024, iterations = 2)))
    }

    @Test
    fun strongerCostsAreNeverLowered() {
        assertFalse(KdfUpgradePolicy.shouldUpgrade(default.copy(memoryKiB = 128 * 1024)))
        assertFalse(KdfUpgradePolicy.shouldUpgrade(default.copy(iterations = default.iterations + 1)))
        assertFalse(KdfUpgradePolicy.shouldUpgrade(default.copy(memoryKiB = 128 * 1024, iterations = 4)))
        assertFalse(KdfUpgradePolicy.shouldUpgrade(KdfParams(KdfParams.MAX_MEMORY_KIB, KdfParams.MAX_ITERATIONS, KdfParams.MAX_PARALLELISM)))
        // Lanes alone change neither the memory nor the passes: nothing to rewrite.
        assertFalse(KdfUpgradePolicy.shouldUpgrade(default.copy(parallelism = 1)))
        assertFalse(KdfUpgradePolicy.shouldUpgrade(default.copy(parallelism = 8)))
    }

    @Test
    fun theTargetIsTheDefaultUnlessGiven() {
        val raised = default.copy(memoryKiB = 128 * 1024, iterations = 4)
        assertTrue(KdfUpgradePolicy.shouldUpgrade(default, target = raised))
        assertFalse(KdfUpgradePolicy.shouldUpgrade(raised, target = raised))
    }
}
