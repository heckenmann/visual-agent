package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.mockk
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentManagerToolHistoryPersistenceTest {
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
                phase = ToolCallPhase.FINISHED,
                inputJson = """{"action":"list"}""",
                context = mapOf("sessionId" to "main"),
                result = ToolResult(toolId = "todos", success = true, content = "- [PENDING] A"),
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
                result = ToolResult(toolId = "todos", success = true, content = "- [IN_PROGRESS] Example"),
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
