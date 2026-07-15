@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.config.ThemeMode
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for the reusable settings execution and appearance section.
 */
class ComposeSettingsExecutionAndAppearanceSectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `section renders execution and appearance controls`() {
        composeTestRule.setContent {
            val contextLength = remember { mutableStateOf(4096) }
            val loadLimit = remember { mutableStateOf("50") }
            val parallel = remember { mutableStateOf("4") }
            val timeout = remember { mutableStateOf("120") }
            val stream = remember { mutableStateOf(true) }
            val think = remember { mutableStateOf(false) }
            val compact = remember { mutableStateOf(true) }
            val instruction = remember { mutableStateOf("Be concise") }
            val fontSize = remember { mutableStateOf(14) }

            MaterialTheme {
                SettingsExecutionAndAppearanceSection(
                    config = AppConfig.instance,
                    contextLength = contextLength.value,
                    loadLimit = loadLimit.value,
                    maxParallelSubAgents = parallel.value,
                    timeoutSeconds = timeout.value,
                    streamingEnabled = stream.value,
                    thinkingEnabled = think.value,
                    autoCompactionEnabled = compact.value,
                    userInstruction = instruction.value,
                    fontSize = fontSize.value,
                    themeMode = ThemeMode.SYSTEM,
                    modelCapabilities = setOf("completion", "tools", "thinking"),
                    onContextLengthChange = { contextLength.value = it },
                    onLoadLimitChange = { loadLimit.value = it },
                    onMaxParallelChange = { parallel.value = it },
                    onTimeoutChange = { timeout.value = it },
                    onStreamingChange = { stream.value = it },
                    onThinkingChange = { think.value = it },
                    onCompactionChange = { compact.value = it },
                    onUserInstructionChange = { instruction.value = it },
                    onFontSizeChange = { fontSize.value = it },
                    onThemeModeChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Execution").assertExists()
        composeTestRule.onNodeWithText("Appearance").assertExists()
        composeTestRule.onNodeWithText("Context length").assertExists()
        composeTestRule.onNodeWithText("4096").assertExists()
        composeTestRule.onNodeWithText("Stream").assertExists()
        composeTestRule.onNodeWithText("Reasoning").assertExists()
        composeTestRule.onNodeWithText("Compaction").assertExists()
        composeTestRule.onNodeWithText("Model instruction").assertExists()
        composeTestRule.onNodeWithText("Font size").assertExists()
        composeTestRule.onNodeWithText("14 px").assertExists()
    }

    @Test
    fun `model instruction change invokes callback`() {
        var current = ""
        composeTestRule.setContent {
            MaterialTheme {
                SettingsExecutionAndAppearanceSection(
                    config = AppConfig.instance,
                    contextLength = 4096,
                    loadLimit = "50",
                    maxParallelSubAgents = "4",
                    timeoutSeconds = "120",
                    streamingEnabled = true,
                    thinkingEnabled = false,
                    autoCompactionEnabled = true,
                    userInstruction = current,
                    fontSize = 14,
                    themeMode = ThemeMode.SYSTEM,
                    onContextLengthChange = {},
                    onLoadLimitChange = {},
                    onMaxParallelChange = {},
                    onTimeoutChange = {},
                    onStreamingChange = {},
                    onThinkingChange = {},
                    onCompactionChange = {},
                    onUserInstructionChange = { current = it },
                    onFontSizeChange = {},
                    onThemeModeChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Model instruction").performTextInput(".")
        assertEquals(".", current)
    }

    @Test
    fun `reasoning checkbox hidden when model lacks thinking capability`() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsExecutionAndAppearanceSection(
                    config = AppConfig.instance,
                    contextLength = 4096,
                    loadLimit = "50",
                    maxParallelSubAgents = "4",
                    timeoutSeconds = "120",
                    streamingEnabled = true,
                    thinkingEnabled = true,
                    autoCompactionEnabled = true,
                    userInstruction = "",
                    fontSize = 14,
                    themeMode = ThemeMode.SYSTEM,
                    modelCapabilities = setOf("completion", "tools"),
                    onContextLengthChange = {},
                    onLoadLimitChange = {},
                    onMaxParallelChange = {},
                    onTimeoutChange = {},
                    onStreamingChange = {},
                    onThinkingChange = {},
                    onCompactionChange = {},
                    onUserInstructionChange = {},
                    onFontSizeChange = {},
                    onThemeModeChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Stream").assertExists()
        composeTestRule.onNodeWithText("Compaction").assertExists()
        kotlin.test.assertFailsWith<java.lang.AssertionError> {
            composeTestRule.onNodeWithText("Reasoning").assertExists()
        }
    }

    @Test
    fun `reasoning checkbox shown when model supports thinking`() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsExecutionAndAppearanceSection(
                    config = AppConfig.instance,
                    contextLength = 4096,
                    loadLimit = "50",
                    maxParallelSubAgents = "4",
                    timeoutSeconds = "120",
                    streamingEnabled = true,
                    thinkingEnabled = true,
                    autoCompactionEnabled = true,
                    userInstruction = "",
                    fontSize = 14,
                    themeMode = ThemeMode.SYSTEM,
                    modelCapabilities = setOf("completion", "tools", "thinking"),
                    onContextLengthChange = {},
                    onLoadLimitChange = {},
                    onMaxParallelChange = {},
                    onTimeoutChange = {},
                    onStreamingChange = {},
                    onThinkingChange = {},
                    onCompactionChange = {},
                    onUserInstructionChange = {},
                    onFontSizeChange = {},
                    onThemeModeChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Reasoning").assertExists()
    }

    @Test
    fun `capability labels shown when model has capabilities`() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsExecutionAndAppearanceSection(
                    config = AppConfig.instance,
                    contextLength = 4096,
                    loadLimit = "50",
                    maxParallelSubAgents = "4",
                    timeoutSeconds = "120",
                    streamingEnabled = true,
                    thinkingEnabled = false,
                    autoCompactionEnabled = true,
                    userInstruction = "",
                    fontSize = 14,
                    themeMode = ThemeMode.SYSTEM,
                    modelCapabilities = setOf("completion", "tools", "thinking"),
                    onContextLengthChange = {},
                    onLoadLimitChange = {},
                    onMaxParallelChange = {},
                    onTimeoutChange = {},
                    onStreamingChange = {},
                    onThinkingChange = {},
                    onCompactionChange = {},
                    onUserInstructionChange = {},
                    onFontSizeChange = {},
                    onThemeModeChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Capabilities:").assertExists()
        composeTestRule.onNodeWithText("completion").assertExists()
        composeTestRule.onNodeWithText("tools").assertExists()
        composeTestRule.onNodeWithText("thinking").assertExists()
    }

    @Test
    fun `numeric fields filter non-digits`() {
        var loadLimit = "50"
        composeTestRule.setContent {
            MaterialTheme {
                SettingsExecutionAndAppearanceSection(
                    config = AppConfig.instance,
                    contextLength = 4096,
                    loadLimit = loadLimit,
                    maxParallelSubAgents = "4",
                    timeoutSeconds = "120",
                    streamingEnabled = true,
                    thinkingEnabled = false,
                    autoCompactionEnabled = true,
                    userInstruction = "",
                    fontSize = 14,
                    themeMode = ThemeMode.SYSTEM,
                    onContextLengthChange = {},
                    onLoadLimitChange = { loadLimit = it },
                    onMaxParallelChange = {},
                    onTimeoutChange = {},
                    onStreamingChange = {},
                    onThinkingChange = {},
                    onCompactionChange = {},
                    onUserInstructionChange = {},
                    onFontSizeChange = {},
                    onThemeModeChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Startup history").performTextInput("abc")
        assertEquals("50", loadLimit)
    }
}
