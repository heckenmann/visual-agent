package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.model.ToolLiveState
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveTextPresentationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `native paragraph reserves only the changed digit and does not grow during its flip`() {
        var text by mutableStateOf("Remaining 12 seconds")
        val waiting = AtomicBoolean(false)
        val changedLayout = AtomicReference<TextLayoutResult>()
        var initialHeight = 0
        compose.registerIdlingResource(
            object : IdlingResource {
                override val isIdleNow: Boolean
                    get() = !waiting.get()
            },
        )
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(240.dp)) {
                    LiveAnimatedText(text, "execution", MaterialTheme.typography.bodyMedium, maxLines = 2, onTextLayout = { result ->
                        if (result.layoutInput.placeholders.isNotEmpty()) {
                            changedLayout.set(result)
                            waiting.set(false)
                        } else if (initialHeight == 0) {
                            initialHeight = result.size.height
                        }
                    })
                }
            }
        }
        compose.runOnIdle {
            waiting.set(true)
            text = "Remaining 11 seconds"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(
                1,
                changedLayout
                    .get()
                    .layoutInput.placeholders.size,
            )
            assertEquals(initialHeight, changedLayout.get().size.height)
        }
    }

    @Test
    fun `overwide changed words retain native wrapping instead of forcing an inline slot`() {
        compose.setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val paragraph =
                liveInlineParagraph(
                    listOf(LiveTextPiece("old", "a very long replacement")),
                    MaterialTheme.typography.bodyMedium,
                    measurer,
                    density,
                    1,
                )
            assertNull(paragraph)
        }
    }

    @Test
    fun `compact text is limited to two lines and keeps its height after an update`() {
        var text by mutableStateOf("Waiting 12 seconds")
        var height = 0
        var lines = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(140.dp)) {
                    LiveAnimatedText(
                        text,
                        "execution",
                        MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        reducedMotion = true,
                        onTextLayout = {
                            height = it.size.height
                            lines = it.lineCount
                        },
                    )
                }
            }
        }
        var initialHeight = 0
        compose.runOnIdle {
            initialHeight = height
            text = "Now processing a longer sentence with another value 11"
        }
        compose.waitForIdle()
        compose.onNodeWithText(text).assertExists()
        compose.runOnIdle {
            assertEquals(initialHeight, height)
            assertTrue(lines <= 2)
        }
    }

    @Test
    fun `expand exposes all retained entries and collapse restores the compact preview`() {
        val logs = List(20) { "Retained entry $it" }
        val state = ToolLiveState("execution", 1, "Running", logs = logs)
        compose.setContent { MaterialTheme { Box(Modifier.width(240.dp)) { ToolLiveTextSection(state) } } }
        compose.onNodeWithText(logs.takeLast(6).joinToString("\n")).assertExists()
        compose.onNodeWithContentDescription("Expand live details").performClick()
        compose.onNodeWithText("20 retained log entries").assertExists()
        compose.onNodeWithText(logs.joinToString("\n")).assertExists()
        compose.onNodeWithContentDescription("Collapse live details").performClick()
        compose.onNodeWithText(logs.takeLast(6).joinToString("\n")).assertExists()
        compose.onNodeWithText("20 retained log entries").assertDoesNotExist()
    }
}
