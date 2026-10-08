package io.github.jls97.boveda.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Test

/** El aviso del freno dice cuánto esperar, redondeado hacia arriba y nunca menos de 1 s (R02-6). */
class ThrottledMessageTest {

    private val now = 1_700_000_000_000L

    @Test
    fun wholeSecondsAreShownAsTheyAre() {
        assertEquals("Demasiados intentos fallidos. Vuelve a intentarlo en 30 s.", throttledMessage(now + 30_000L, now))
        assertEquals("Demasiados intentos fallidos. Vuelve a intentarlo en 3840 s.", throttledMessage(now + 64L * 60_000L, now))
    }

    @Test
    fun aFractionOfASecondRoundsUp() {
        assertEquals("Demasiados intentos fallidos. Vuelve a intentarlo en 30 s.", throttledMessage(now + 29_001L, now))
        assertEquals("Demasiados intentos fallidos. Vuelve a intentarlo en 1 s.", throttledMessage(now + 1L, now))
    }

    @Test
    fun aDeadlineAlreadyReachedStillAsksForAtLeastOneSecond() {
        assertEquals("Demasiados intentos fallidos. Vuelve a intentarlo en 1 s.", throttledMessage(now, now))
        assertEquals("Demasiados intentos fallidos. Vuelve a intentarlo en 1 s.", throttledMessage(now - 5_000L, now))
    }
}
