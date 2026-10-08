package io.github.jls97.boveda

import android.view.View
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jls97.boveda.ui.components.SecureAlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dialogWindowIsExcludedFromAutofill() {
        // The dialog lives in its own window: its root view, not the Activity's, must carry the policy.
        var dialogRoot: View? = null
        composeTestRule.setContent {
            SecureAlertDialog(
                onDismissRequest = {},
                title = { Text("Diálogo seguro") },
                text = {
                    val view = LocalView.current
                    SideEffect { dialogRoot = view.rootView }
                },
                confirmButton = { TextButton(onClick = {}) { Text("Aceptar") } },
            )
        }
        composeTestRule.onNodeWithText("Diálogo seguro").assertExists()
        composeTestRule.waitForIdle()
        val root = requireNotNull(dialogRoot)
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, root.importantForAutofill)
        assertTrue(root.filterTouchesWhenObscured)
    }
}
