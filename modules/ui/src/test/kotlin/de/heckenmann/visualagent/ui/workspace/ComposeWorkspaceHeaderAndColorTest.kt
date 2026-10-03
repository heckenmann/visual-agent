@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ComposeWorkspaceHeaderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `workspace header renders identity without status badges`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeWorkspaceHeader(
                    inFlight = InFlightState(),
                    onStopAll = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Visual Agent").assertExists()
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithContentDescription("Visual Agent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithContentDescription("Visual Agent")
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .assertWidthIsEqualTo(40.dp)
            .assertHeightIsEqualTo(40.dp)
        composeTestRule.onNodeWithText("Compose Multiplatform workspace").assertExists()
        val titleBounds = composeTestRule.onNodeWithText("Visual Agent").fetchSemanticsNode().boundsInRoot
        val subtitleBounds = composeTestRule.onNodeWithText("Compose Multiplatform workspace").fetchSemanticsNode().boundsInRoot
        assertEquals(titleBounds.left, subtitleBounds.left)
        val logoBounds = composeTestRule.onNodeWithContentDescription("Visual Agent").fetchSemanticsNode().boundsInRoot
        assertEquals((titleBounds.top + subtitleBounds.bottom) / 2f, logoBounds.center.y)
        composeTestRule.onNodeWithText("Provider", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Model", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Beans", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Context", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Tools unavailable").assertDoesNotExist()
    }
}

class ComposeCanvasColorHelperTest {
    @Test
    fun `toComposeColor parses hex or returns default`() {
        assertEquals(Color(0xFFFF0000.toInt()), "FF0000".toComposeColor(Color.Black))
        assertEquals(Color(0xFF00FF00.toInt()), "#00FF00".toComposeColor(Color.Black))
        assertEquals(Color.Black, "not-a-color".toComposeColor(Color.Black))
    }
}
