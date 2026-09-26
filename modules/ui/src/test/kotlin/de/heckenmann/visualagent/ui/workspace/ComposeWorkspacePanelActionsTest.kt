package de.heckenmann.visualagent.ui.workspace

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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

    @Test
    fun `panel header icons use the active theme secondary accent`() {
        val accent = Color.Red
        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color.Green, secondary = accent, tertiary = Color.Blue)) {
                Row {
                    PanelHeaderIdentityIcon(
                        window =
                            ComposeWorkspaceWindow(
                                id = "files",
                                icon = "folder",
                                title = "Files",
                                subtitle = "Workspace files",
                                bounds = ComposeWorkspaceWindowBounds(0, 0, 320, 400),
                            ),
                        primary = false,
                    )
                    workspacePanelHeaderActions("Files", false, onOpenSettings = {}, onClose = {})
                }
            }
        }

        listOf(
            composeTestRule.onNodeWithTag("workspace-panel-identity-icon"),
            composeTestRule.onNodeWithContentDescription("Configure providers and models"),
            composeTestRule.onNodeWithContentDescription("Close Files panel"),
        ).forEach { node ->
            val pixels = node.captureToImage().toPixelMap()
            assertTrue(
                (0 until pixels.width).any { x ->
                    (0 until pixels.height).any { y ->
                        val pixel = pixels[x, y]
                        pixel.red > 0.9f && pixel.green < 0.1f && pixel.blue < 0.1f
                    }
                },
            )
        }
    }
}
