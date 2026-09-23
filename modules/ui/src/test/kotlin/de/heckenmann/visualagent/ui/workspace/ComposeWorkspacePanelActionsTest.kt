package de.heckenmann.visualagent.ui.workspace

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies panel-header settings and close buttons dispatch their callbacks. */
class ComposeWorkspacePanelActionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `settings and close actions invoke their handlers`() {
        var settingsOpened = false
        var panelClosed = false
        composeTestRule.setContent {
            MaterialTheme {
                workspacePanelHeaderActions(
                    panelTitle = "Files",
                    primary = false,
                    onOpenSettings = { settingsOpened = true },
                    onClose = { panelClosed = true },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Configure providers and models")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeTestRule.onNodeWithContentDescription("Close Files panel").performSemanticsAction(SemanticsActions.OnClick) { it() }

        assertTrue(settingsOpened)
        assertTrue(panelClosed)
    }

    @Test
    fun `non-configurable panels omit settings action`() {
        var closed = false
        composeTestRule.setContent {
            MaterialTheme {
                workspacePanelHeaderActions("Canvas", false, onOpenSettings = null, onClose = { closed = true })
            }
        }

        composeTestRule.onNodeWithContentDescription("Configure providers and models").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Close Canvas panel").performSemanticsAction(SemanticsActions.OnClick) { it() }

        assertTrue(closed)
    }
}
