package io.github.jls97.boveda.session

/**
 * Pure decision of [VaultSession.restoreBackup]: does restoring a backup need the master password
 * of the vault that is already on this phone? No Android, so it is tested on the JVM.
 *
 * Restoring replaces that vault (a copy is kept for an undo, but only until the next restore), so
 * writing over it needs proof that it belongs to whoever is restoring:
 * - With no vault on the phone there is nothing to lose: nothing to check.
 * - With the vault unlocked, the current master password is required, always: it is at hand, and
 *   an open vault is the state a borrowed phone is most likely to be in.
 * - With the vault locked, the current password is checked if given. Without it, the caller must
 *   say so explicitly ([Decision.FORCED], a "I do not remember it" path behind a strong
 *   confirmation in the UI); otherwise the restore is refused.
 */
object RestorePolicy {
    enum class Decision {
        /** No vault on this phone: nothing to protect, write right away. */
        NO_VAULT,

        /** Check the current master password before writing. */
        VERIFY_CURRENT,

        /** The vault is locked and the caller took responsibility: write without checking. */
        FORCED,

        /** Refuse: the vault on this phone could be lost without proof that it is the user's. */
        REFUSE,
    }

    fun decide(
        vaultOnPhone: Boolean,
        unlocked: Boolean,
        currentPasswordGiven: Boolean,
        forceWithoutCurrent: Boolean,
    ): Decision = when {
        !vaultOnPhone -> Decision.NO_VAULT
        currentPasswordGiven -> Decision.VERIFY_CURRENT
        unlocked -> Decision.REFUSE
        forceWithoutCurrent -> Decision.FORCED
        else -> Decision.REFUSE
    }

    /**
     * Whether a successful restore may clear the wrong-password throttle, as a successful unlock
     * does. A forced restore proved nothing about the owner, so the count stays.
     */
    fun resetsThrottle(decision: Decision): Boolean = decision != Decision.FORCED

    /**
     * Whether the UI must warn that the master password is now the backup's. Unknown without the
     * current password (nothing to compare with), so it warns whenever a vault was replaced.
     */
    fun masterPasswordChanges(vaultOnPhone: Boolean, currentPasswordGiven: Boolean, samePassword: Boolean): Boolean =
        vaultOnPhone && !(currentPasswordGiven && samePassword)
}
