package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerToolHistoryPersistenceTest {
    @Test
    fun `tool lifecycle updates one child row and preserves parent through paging and restart`() {
        val tempDb = createTempDirectory("visual-agent-turn-history-test").resolve("history.db").toString()
        val db1 =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val manager1 =
            AgentManager(db1, mockk(relaxed = true), AgentToolConfigService(db1), ToolEventBus(), TodoEventBus(), AppConfigBean(db1))
        val parentId = UUID.randomUUID().toString()
        db1.saveConversationMessage(
            parentId,
            "main",
            "assistant",
            "Inspecting the configuration",
            "{}",
            ConversationContextPolicy.DIALOGUE,
            assistantToolTurn = true,
        )
        val now = Instant.now()
        val baseEvent =
            ToolCallEvent(
                toolId = "file:read",
                functionName = "file_read",
                providerToolCallId = "call-1",
                requestId = UUID.randomUUID().toString(),
                round = 0,
                sequence = 0,
                parentAssistantTurnId = parentId,
                inputJson = """{"path":"app.properties"}""",
                context = mapOf("agent" to "main", "sessionId" to "main"),
                result = ToolResult("file:read", true, ""),
                startedAtUtc = now,
                finishedAtUtc = now,
                durationMillis = 0,
            )

        manager1.recordToolCall(baseEvent.copy(phase = ToolCallPhase.STARTED))
        val rowWhileRunning = db1.getConversationMessages("main", 10).single { it.role == "tool" }
        assertTrue(rowWhileRunning.content.contains("running"))
        manager1.recordToolCall(
            baseEvent.copy(
                phase = ToolCallPhase.FINISHED,
                result = ToolResult("file:read", true, "configuration contents"),
                durationMillis = 28,
            ),
        )

        val completedRow = db1.getConversationMessages("main", 10).single { it.role == "tool" }
        assertEquals(rowWhileRunning.id, completedRow.id)
        assertEquals(parentId, completedRow.parentAssistantTurnId)
        assertEquals(0, completedRow.turnOrder)
        assertTrue(completedRow.content.contains("configuration contents"))
        val page = db1.getConversationHistoryPage("main", 1, 0)
        assertEquals(setOf(parentId, completedRow.id), page.records.map { it.id }.toSet())
        assertEquals(1, page.nextOffset)
        assertTrue(page.hasMore)
        val nextPage = db1.getConversationHistoryPage("main", 1, page.nextOffset)
        assertEquals(setOf(parentId, completedRow.id), nextPage.records.map { it.id }.toSet())
        assertEquals(2, nextPage.nextOffset)
        assertTrue(!nextPage.hasMore)
        db1.close()

        val db2 =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val manager2 =
            AgentManager(db2, mockk(relaxed = true), AgentToolConfigService(db2), ToolEventBus(), TodoEventBus(), AppConfigBean(db2))
        val restored = manager2.getHistory()
        assertTrue(restored.any { it.id == parentId && it.assistantToolTurn })
        assertTrue(restored.any { it.parentAssistantTurnId == parentId && it.turnOrder == 0 })
        db2.close()
    }

    @Test
    fun `finished tool call is persisted in conversation history with metadata`() {
        val tempDb = createTempDirectory("visual-agent-tool-history-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
        val now = Instant.now()
        val event =
            ToolCallEvent(
                toolId = "todos",
                functionName = "todos",
                providerToolCallId = "call-42",
                requestId = "request-7",
                round = 1,
                sequence = 2,
                phase = ToolCallPhase.FINISHED,
                inputJson = """{"action":"list"}""",
                context = mapOf("sessionId" to "main"),
                result =
                    de.heckenmann.visualagent.agent.tools.api
                        .ToolResult(toolId = "todos", success = true, content = "- [PENDING] A"),
                startedAtUtc = now,
                finishedAtUtc = now,
                durationMillis = 10,
            )

        manager.recordToolCall(event)

        val historyRows = db.getConversationMessages("main", 50)
        val last = historyRows.last()
        assertEquals("tool", last.role)
        assertTrue(last.content.startsWith("Tool todos"))
        assertTrue(last.metadata.orEmpty().contains("\"type\":\"tool_call\""))
        assertTrue(last.metadata.orEmpty().contains("\"providerToolCallId\":\"call-42\""))
        assertTrue(last.metadata.orEmpty().contains("\"requestId\":\"request-7\""))
        assertTrue(last.metadata.orEmpty().contains("\"round\":1"))
        assertTrue(last.metadata.orEmpty().contains("\"sequence\":2"))
        db.close()
    }

    @Test
    fun `tool timeout and cancellation statuses keep their explicit parent relationship`() {
        val tempDb = createTempDirectory("visual-agent-tool-failure-history-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val manager = AgentManager(db, mockk(relaxed = true), AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
        val parentId = UUID.randomUUID().toString()
        db.saveConversationMessage(
            parentId,
            "main",
            "assistant",
            "Checking the service",
            "{}",
            ConversationContextPolicy.DIALOGUE,
            assistantToolTurn = true,
        )
        val now = Instant.now()
        val baseEvent =
            ToolCallEvent(
                toolId = "terminal",
                functionName = "terminal",
                requestId = UUID.randomUUID().toString(),
                round = 0,
                parentAssistantTurnId = parentId,
                inputJson = "{}",
                context = mapOf("agent" to "main", "sessionId" to "main"),
                result = ToolResult("terminal", false, "", "TOOL_TIMEOUT: deadline reached"),
                startedAtUtc = now,
                finishedAtUtc = now,
                durationMillis = 100,
            )

        manager.recordToolCall(baseEvent.copy(sequence = 0, providerToolCallId = "timeout-call"))
        manager.recordToolCall(
            baseEvent.copy(
                sequence = 1,
                providerToolCallId = "cancelled-call",
                result = ToolResult("terminal", false, "", "TOOL_CANCELLED: user cancelled"),
            ),
        )

        val children = db.getConversationMessages("main", 10).filter { it.role == "tool" }
        assertTrue(children[0].metadata.orEmpty().contains("\"status\":\"timeout\""))
        assertTrue(children[1].metadata.orEmpty().contains("\"status\":\"cancelled\""))
        assertTrue(children.all { it.parentAssistantTurnId == parentId })
        assertEquals(listOf(0, 1), children.map { it.turnOrder })
        db.close()
    }

    @Test
    fun `persisted tool call is loaded again after manager restart`() {
        val tempDb = createTempDirectory("visual-agent-tool-history-restart-test").resolve("history.db").toString()
        val db1 =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider1 = mockk<LLMProvider>(relaxed = true)
        val manager1 = AgentManager(db1, provider1, AgentToolConfigService(db1), ToolEventBus(), TodoEventBus(), AppConfigBean(db1))
        val now = Instant.now()
        manager1.recordToolCall(
            ToolCallEvent(
                toolId = "todos",
                functionName = "todos",
                phase = ToolCallPhase.FINISHED,
                inputJson = """{"action":"list"}""",
                context = mapOf("sessionId" to "main"),
                result =
                    de.heckenmann.visualagent.agent.tools.api.ToolResult(
                        toolId = "todos",
                        success = true,
                        content = "- [IN_PROGRESS] Example",
                    ),
                startedAtUtc = now,
                finishedAtUtc = now,
                durationMillis = 9,
            ),
        )
        db1.close()

        val db2 =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider2 = mockk<LLMProvider>(relaxed = true)
        val manager2 = AgentManager(db2, provider2, AgentToolConfigService(db2), ToolEventBus(), TodoEventBus(), AppConfigBean(db2))
        val history = manager2.getHistory()
        assertTrue(history.any { it.content.startsWith("Tool todos") })
        assertTrue(history.any { it.metadata?.contains("\"type\":\"tool_call\"") == true })
        db2.close()
    }
}
