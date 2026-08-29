@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

class ConversationPanelRowsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun mount(
        width: Int = 900,
        height: Int = 400,
        content: @Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width.dp, height.dp)) {
                    content()
                }
            }
        }
    }

    @Test
    fun `tool row shows tool id and expands to show input and result`() {
        val metadata =
            buildJsonObject {
                put("type", "tool_call")
                put("toolId", "file:read")
                put("functionName", "fileRead")
                put("status", "ok")
                put("durationMillis", 42L)
                put("inputJson", "{\"path\":\"/tmp/hi.txt\"}")
                put("resultContent", "hello world")
                put("resultError", "")
            }.toString()
        mount {
            ToolMessageRow(
                message = Message(role = "tool", content = "Tool file:read · ok · hello world", metadata = metadata),
                isDeleting = false,
                isInFlight = false,
                onDelete = {},
            )
        }
        composeTestRule.onNodeWithText("file:read").assertExists()
        composeTestRule.onNodeWithText("42ms").assertExists()
        // Hidden initially.
        composeTestRule.onNodeWithText("hello world").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Expand tool details").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("hello world").assertExists()
        composeTestRule.onNodeWithText("Input").assertExists()
        composeTestRule.onNodeWithText("Result").assertExists()
    }

    @Test
    fun `tool row shows running spinner while in flight`() {
        val metadata =
            buildJsonObject {
                put("toolId", "file:read")
                put("status", "ok")
            }.toString()
        mount {
            ToolMessageRow(
                message = Message(role = "tool", content = "", metadata = metadata),
                isDeleting = false,
                isInFlight = true,
                onDelete = {},
            )
        }
        composeTestRule.onNodeWithText("running…").assertExists()
    }

    @Test
    fun `tool row error state uses error tint`() {
        val metadata =
            buildJsonObject {
                put("toolId", "file:read")
                put("status", "error")
                put("resultError", "boom")
            }.toString()
        mount {
            ToolMessageRow(
                message = Message(role = "tool", content = "", metadata = metadata),
                isDeleting = false,
                isInFlight = false,
                onDelete = {},
            )
        }
        composeTestRule.onNodeWithText("file:read").assertExists()
    }

    @Test
    fun `sub-agent row renders agent name and success summary`() {
        val metadata =
            buildJsonObject {
                put("type", "sub_agent")
                put("jobId", "job-1")
                put("success", true)
                put("agentId", "agent-1")
                put("agentName", "researcher")
            }.toString()
        mount {
            SubAgentMessageRow(
                message =
                    Message(
                        role = "sub_agent",
                        content = "found something",
                        metadata = metadata,
                    ),
                isDeleting = false,
                isRunning = false,
                onDelete = {},
            )
        }
        composeTestRule.onNodeWithText("Agent \"researcher\" completed a job").assertExists()
        // Hidden until expanded.
        composeTestRule.onNodeWithText("found something").assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription("Expand sub-agent details")
            .assertExists()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("found something").assertExists()
    }

    @Test
    fun `sub-agent row shows running chip while agent is busy`() {
        val metadata =
            buildJsonObject {
                put("type", "sub_agent")
                put("success", true)
                put("agentId", "agent-1")
                put("agentName", "coder")
            }.toString()
        mount {
            SubAgentMessageRow(
                message = Message(role = "sub_agent", content = "x", metadata = metadata),
                isDeleting = false,
                isRunning = true,
                onDelete = {},
            )
        }
        composeTestRule.onNodeWithText("Agent \"coder\" is working…").assertExists()
    }

    @Test
    fun `message row delete button invokes onDelete`() {
        var deleted = false
        mount {
            MessageRow(
                message = Message(role = "assistant", content = "hi", id = "msg-1"),
                isStreamingPlaceholder = false,
                isStreaming = false,
                canRetry = false,
                canEdit = false,
                canDelete = true,
                isDeleting = false,
                onCopied = {},
                onRetry = {},
                onEdit = {},
                onDelete = { deleted = true },
            )
        }
        composeTestRule.onNodeWithContentDescription("Delete assistant message").performClick()
        assertTrue(deleted)
    }

    @Test
    fun `message row hides delete button when canDelete is false`() {
        var deleteClicked = false
        mount {
            MessageRow(
                message = Message(role = "assistant", content = "hi", id = "msg-1"),
                isStreamingPlaceholder = false,
                isStreaming = false,
                canRetry = false,
                canEdit = false,
                canDelete = false,
                isDeleting = false,
                onCopied = {},
                onRetry = {},
                onEdit = {},
                onDelete = { deleteClicked = true },
            )
        }
        composeTestRule.onNodeWithContentDescription("Delete assistant message").assertDoesNotExist()
        assertFalse(deleteClicked)
    }

    @Test
    fun `tool row hides delete button when message id is null`() {
        var deleteClicked = false
        val metadata =
            buildJsonObject {
                put("type", "tool_call")
                put("toolId", "pwd")
                put("status", "ok")
            }.toString()
        mount {
            ToolMessageRow(
                message = Message(role = "tool", content = "Tool pwd · ok", metadata = metadata),
                isDeleting = false,
                isInFlight = false,
                onDelete = { deleteClicked = true },
            )
        }
        composeTestRule.onNodeWithContentDescription("Delete tool call").assertDoesNotExist()
        assertFalse(deleteClicked)
    }

    @Test
    fun `sub-agent row hides delete button when message id is null`() {
        var deleteClicked = false
        val metadata =
            buildJsonObject {
                put("type", "sub_agent")
                put("success", true)
                put("agentName", "researcher")
            }.toString()
        mount {
            SubAgentMessageRow(
                message = Message(role = "sub_agent", content = "x", metadata = metadata),
                isDeleting = false,
                isRunning = false,
                onDelete = { deleteClicked = true },
            )
        }
        composeTestRule.onNodeWithContentDescription("Delete sub-agent message").assertDoesNotExist()
        assertFalse(deleteClicked)
    }

    @Test
    fun `tool row metadata parser handles missing fields`() {
        val metadata = Json.parseToJsonElement("{}").let { it as JsonObject }
        val parsed = parseToolMetadata(metadata.toString())
        assertEquals("tool", parsed.toolId)
        assertEquals("ok", parsed.status)
        assertEquals(null, parsed.durationMillis)
    }

    @Test
    fun `thinking row is collapsed by default and expands`() {
        mount {
            ThinkingRow(content = "first line\nlatest line", isStreaming = false)
        }
        composeTestRule.onNodeWithText("Thinking").assertExists()
        composeTestRule.onNodeWithText("latest line").assertExists()
        composeTestRule.onNodeWithText("first line").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Expand thinking").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("first line", substring = true).assertExists()
    }

    @Test
    fun `thinking preview follows the latest non-empty line`() {
        mount {
            ThinkingRow(content = "first\n\nlatest", isStreaming = true)
        }

        composeTestRule.onNodeWithText("latest").assertExists()
        composeTestRule.onNodeWithText("first").assertDoesNotExist()
        val titleBounds = composeTestRule.onNodeWithText("Thinking…").getUnclippedBoundsInRoot()
        val previewBounds = composeTestRule.onNodeWithText("latest").getUnclippedBoundsInRoot()
        assertTrue(previewBounds.top < titleBounds.bottom && titleBounds.top < previewBounds.bottom)
    }

    @Test
    fun `collapsed thinking preview renders its latest line as markdown`() {
        mount {
            ThinkingRow(content = "first\n**latest**", isStreaming = false)
        }

        composeTestRule.onNodeWithText("latest").assertExists()
        composeTestRule.onNodeWithText("**latest**").assertDoesNotExist()
    }
}
