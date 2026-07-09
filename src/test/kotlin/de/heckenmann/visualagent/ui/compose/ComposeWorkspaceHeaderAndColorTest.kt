@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
