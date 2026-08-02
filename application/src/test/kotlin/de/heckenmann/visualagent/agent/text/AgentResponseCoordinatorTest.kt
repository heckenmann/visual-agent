package de.heckenmann.visualagent.agent.text

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentResponseCoordinatorTest {
    private val provider = mockk<LLMProvider>()
    private val events = ConcurrentHashMap<String, MutableList<ToolCallEvent>>()
    private val requests = mutableListOf<ChatRequestContext>()
    private val conversationOps =
        ConversationOpsProvider(mockk<ToolEventBus>(relaxed = true)).apply {
            setBuildMainRequest { history, requestId, _ ->
                ChatRequestContext(history, metadata = mapOf("requestId" to requestId.orEmpty())).also(requests::add)
            }
            setBuildMainSystemContextPrompt { "system context" }
            setLoadRecentHistoryFromDb { listOf(Message("user", "question")) }
        }
    private val coordinator =
        AgentResponseCoordinator(
            llmProvider = provider,
            conversationOps = conversationOps,
        )

    @Test
    fun `normalization handles blank tool-only repeated and regular text`() {
        assertEquals("(No text response. See tool results above.)", coordinator.normalizeAssistantContent(" "))
        assertEquals(
            "(No text response. See tool results above.)",
            coordinator.normalizeAssistantContent("""```json {"tool_calls": []} ```"""),
        )
        assertTrue(coordinator.normalizeAssistantContent("repeat pattern ".repeat(60)).contains("malformed"))
        assertEquals("answer", coordinator.normalizeAssistantContent("  answer  "))
    }

    @Test
    fun `blank response is finalized from captured tool results`() =
        runTest {
            conversationOps.finishedToolEventsByRequestId["request-1"] = mutableListOf(toolEvent(success = true))
            coEvery { provider.chat(any<ChatRequestContext>()) } returnsMany
                listOf(response(""), response("Final answer from tool output"))

            val result = coordinator.generateAssistantContentWithRepetitionGuard("request-1")

            assertEquals("Final answer from tool output", result)
            assertFalse(events.containsKey("request-1"))
            assertTrue(requests.single().metadata["requestId"] == "request-1")
        }

    @Test
    fun `repeated response is retried with anti-repetition instruction`() =
        runTest {
            coEvery { provider.chat(any<ChatRequestContext>()) } returnsMany
                listOf(response("repeat pattern ".repeat(60)), response("clean retry"))

            val result = coordinator.generateAssistantContentWithRepetitionGuard("request-2")

            assertEquals("clean retry", result)
            assertEquals(2, requests.size)
            assertTrue(
                requests
                    .last()
                    .messages
                    .first()
                    .content
                    .contains("question"),
            )
        }

    @Test
    fun `tool followup returns null when no events exist`() =
        runTest {
            assertNull(coordinator.completeToolOnlyTurnWithFollowup("missing"))
        }

    private fun response(content: String) =
        ChatResponse(
            model = "test-model",
            message = Message("assistant", content),
            done = true,
        )

    private fun toolEvent(success: Boolean) =
        ToolCallEvent(
            toolId = "file:read",
            functionName = "file_read",
            inputJson = "{}",
            context = emptyMap(),
            result = ToolResult("file:read", success, "file content"),
            startedAtUtc = Instant.EPOCH,
            finishedAtUtc = Instant.EPOCH,
            durationMillis = 1,
        )
}
