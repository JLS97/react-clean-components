package io.github.jls97.boveda.session

import io.github.jls97.boveda.security.ThrottleClock
import io.github.jls97.boveda.security.ThrottlePolicy
import io.github.jls97.boveda.security.ThrottleState
import io.github.jls97.boveda.security.ThrottleStore
import io.github.jls97.boveda.security.UnlockThrottle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El mecanismo único de comprobación de contraseña bajo el freno (R02-1, R02-2, R02-3), el que
 * usan la re-autenticación de Ajustes y el cambio de contraseña: tras los intentos libres el
 * siguiente devuelve Throttled sin derivar nada, un acierto reinicia el contador y los fallos de
 * la re-autenticación cuentan para el desbloqueo, porque el freno es el mismo.
 */
class ThrottledPasswordCheckTest {

    private class FakeStore : ThrottleStore {
        var state = ThrottleState()
        var kept: ThrottleState? = null

        override fun load(): ThrottleState = state

        override fun save(state: ThrottleState) {
            this.state = state
        }

        override fun clear() {
            state = ThrottleState()
        }

        override fun loadKept(): ThrottleState? = kept

        override fun saveKept(state: ThrottleState?) {
            kept = state
        }
    }

    private class FakeClock(var elapsed: Long = 1_000_000L, var wall: Long = 1_700_000_000_000L) : ThrottleClock {
        override fun elapsedRealtime(): Long = elapsed

        override fun wallClock(): Long = wall

        override fun bootCount(): Int = 7

        fun tick(ms: Long) {
            elapsed += ms
            wall += ms
        }
    }

    private val policy = ThrottlePolicy()
    private val store = FakeStore()
    private val clock = FakeClock()
    private val throttle = UnlockThrottle(store, clock, policy)

    /** Cuántas veces se ha derivado la contraseña (lo caro, lo que el freno debe evitar). */
    private var derivations = 0

    private fun check(correct: Boolean): OperationResult = runBlocking {
        throttle.checkPassword {
            derivations++
            correct
        }
    }

    @Test
    fun aWrongPasswordIsReportedAndCountedWhileAttemptsAreFree() {
        repeat(policy.freeAttempts - 1) { assertEquals(OperationResult.WrongPassword, check(correct = false)) }
        assertEquals(policy.freeAttempts - 1, store.state.failures)
        assertEquals(0L, throttle.blockedUntil())
    }

    @Test
    fun afterTheFreeAttemptsTheNextCheckIsThrottledWithoutDerivingAnything() {
        repeat(policy.freeAttempts - 1) { check(correct = false) }
        // El quinto fallo ya devuelve la espera...
        val fifth = check(correct = false)
        assertTrue(fifth is OperationResult.Throttled)
        assertEquals(clock.wall + policy.baseDelayMs, (fifth as OperationResult.Throttled).untilMillis)
        assertEquals(policy.freeAttempts, derivations)
        // ...y mientras dura, ni la contraseña correcta se comprueba: no cuesta un Argon2id ni
        // cuenta como fallo.
        val blocked = check(correct = true)
        assertEquals(fifth, blocked)
        assertEquals(policy.freeAttempts, derivations)
        assertEquals(policy.freeAttempts, store.state.failures)
    }

    @Test
    fun aCorrectPasswordResetsTheCounter() {
        repeat(policy.freeAttempts - 1) { check(correct = false) }
        assertEquals(OperationResult.Success, check(correct = true))
        assertEquals(ThrottleState(), store.state)
        // Vuelta al principio: otra vez los intentos libres.
        repeat(policy.freeAttempts - 1) { assertEquals(OperationResult.WrongPassword, check(correct = false)) }
    }

    @Test
    fun onceServedTheBlockLiftsAndTheNextFailureDoublesIt() {
        repeat(policy.freeAttempts) { check(correct = false) }
        clock.tick(policy.baseDelayMs)
        val sixth = check(correct = false)
        assertTrue(sixth is OperationResult.Throttled)
        assertEquals(2 * policy.baseDelayMs, (sixth as OperationResult.Throttled).untilMillis - clock.wall)
        assertEquals(policy.freeAttempts + 1, derivations)
    }

    @Test
    fun failuresInReauthenticationBlockTheUnlockToo() {
        // Seis contraseñas mal en el diálogo de Ajustes con la bóveda abierta por huella...
        repeat(policy.freeAttempts + 1) { check(correct = false) }
        // ...y el desbloqueo, que consulta el mismo freno antes de derivar, también está parado.
        val until = throttle.blockedUntil()
        assertTrue(until > 0L)
        assertEquals(policy.baseDelayMs, until - clock.wall)
        assertFalse(store.state.penaltyMs == 0L)
    }
}
