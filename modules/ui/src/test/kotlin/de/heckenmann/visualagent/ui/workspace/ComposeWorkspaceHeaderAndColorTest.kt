@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    fun `workspace header renders provider model and bean chips`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeWorkspaceHeader(
                    providerName = "Ollama",
                    modelName = "llava",
                    beanDefinitionCount = 42,
                    inFlight = InFlightState(),
                    onStopAll = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Visual Agent").assertExists()
        composeTestRule.onNodeWithText("Provider Ollama").assertExists()
        composeTestRule.onNodeWithText("Model llava").assertExists()
        composeTestRule.onNodeWithText("Beans 42").assertExists()
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
