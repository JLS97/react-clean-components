package io.github.jls97.boveda

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jls97.boveda.session.VaultState
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val session get() = (composeTestRule.activity.application as BovedaApplication).session

    @Test
    fun startsClosed() {
        // Un arranque en frío nunca muestra la lista de entradas: solo la pantalla de creación (sin
        // bóveda) o la de desbloqueo (bóveda cerrada). El título «Bóveda» no sirve como prueba porque
        // también encabeza la lista; se comprueban las etiquetas de las pantallas raíz (I-26).
        val setup = composeTestRule.onAllNodes(hasTestTag("setup_screen")).fetchSemanticsNodes().isNotEmpty()
        val unlock = composeTestRule.onAllNodes(hasTestTag("unlock_screen")).fetchSemanticsNodes().isNotEmpty()
        assertTrue("Debe verse la pantalla de creación o la de desbloqueo", setup || unlock)
        composeTestRule.onNodeWithTag("entry_list").assertDoesNotExist()
    }

    @Test
    fun showsSetupWhenNoVault() {
        // Con la bóveda sin crear (instalación limpia) se muestra la pantalla de creación y nunca la
        // lista de entradas. En un dispositivo con bóveda ya creada el caso no aplica y se omite.
        assumeTrue("Requiere una instalación sin bóveda creada", session.state.value == VaultState.NoVault)
        composeTestRule.onNodeWithTag("setup_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unlock_screen").assertDoesNotExist()
        composeTestRule.onNodeWithTag("entry_list").assertDoesNotExist()
    }
}
