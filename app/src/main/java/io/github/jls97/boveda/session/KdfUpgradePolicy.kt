package io.github.jls97.boveda.session

import io.github.jls97.boveda.core.crypto.KdfParams

/**
 * Pure decision of [VaultSession.unlock]: must a vault that has just opened with the master
 * password be written again with the app's current Argon2id costs? No Android, so it is tested
 * on the JVM.
 *
 * The costs live in the header and nothing else ever rewrote it, so a vault created when the
 * app used cheaper costs (or restored from such a backup) stayed an easier offline target for
 * good. A password unlock has the password at hand, which is all a new header needs; the DEK
 * does not change, so the fingerprint copy keeps working. Costs stronger than the default are
 * never lowered: whoever set them wanted them.
 */
object KdfUpgradePolicy {
    /** True if a header with [current] costs must be rebuilt with [target] (the app's default). */
    fun shouldUpgrade(current: KdfParams, target: KdfParams = KdfParams.DEFAULT): Boolean = current.isWeakerThan(target)
}
