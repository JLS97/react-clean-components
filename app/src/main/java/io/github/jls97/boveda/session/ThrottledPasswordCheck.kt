package io.github.jls97.boveda.session

import io.github.jls97.boveda.security.UnlockThrottle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Único mecanismo para comprobar una contraseña maestra bajo el freno de intentos (A-03, B-30,
 * R02-1): consulta el freno antes de derivar nada, registra el fallo y reinicia el contador con el
 * acierto. Toda verificación nueva (desbloqueo, cambio de contraseña, re-autenticación...) debe
 * pasar por aquí para que no vuelva a quedar fuera del freno.
 *
 * [verify] hace la comprobación (Argon2id, en el despachador que elija) y devuelve true si la
 * contraseña es correcta; no se llama mientras el freno está activo. El freno se lee y escribe en
 * [Dispatchers.IO]: es disco.
 */
internal suspend fun UnlockThrottle.checkPassword(verify: suspend () -> Boolean): OperationResult {
    val blockedUntil = withContext(Dispatchers.IO) { blockedUntil() }
    if (blockedUntil > 0) return OperationResult.Throttled(blockedUntil)
    if (!verify()) {
        val until = withContext(Dispatchers.IO) { recordFailure() }
        return if (until > 0) OperationResult.Throttled(until) else OperationResult.WrongPassword
    }
    withContext(Dispatchers.IO) { reset() }
    return OperationResult.Success
}
