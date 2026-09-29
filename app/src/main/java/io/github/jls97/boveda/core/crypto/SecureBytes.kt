package io.github.jls97.boveda.core.crypto

import java.security.SecureRandom

/** Shared CSPRNG. SecureRandom is thread-safe and seeds itself from the OS. */
val secureRandom: SecureRandom = SecureRandom()

fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

/**
 * Overwrites the array with zeros. This is best effort: the JVM or the platform may keep other
 * copies, but it shortens how long secrets stay in memory.
 */
fun ByteArray.wipe() = fill(0)

fun CharArray.wipe() = fill('\u0000')
