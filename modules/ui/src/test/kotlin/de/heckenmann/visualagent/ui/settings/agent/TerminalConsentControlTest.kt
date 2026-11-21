package de.heckenmann.visualagent.ui.settings.agent

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class TerminalConsentControlTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `terminal activation requires fresh acceptance of the risks`() {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme {
                TerminalConsentControl("server", "Server account", emptyList(), false, true, false, null) {
                    changes.add(it)
                }
            }
        }

        fun openDialog() = compose.onNodeWithContentDescription("Allow terminal on server").performClick()
        val confirm = compose.onNodeWithText("Enable terminal")
        val acceptance = compose.onNodeWithText("I understand and accept the risks.")

        openDialog()
        confirm.assertIsNotEnabled()
        acceptance.performClick()
        compose.onNodeWithText("Cancel").performClick()

        openDialog()
        confirm.assertIsNotEnabled()
        acceptance.performClick()
        confirm.performClick()

        compose.runOnIdle { assertEquals(listOf(true), changes) }
    }
}
