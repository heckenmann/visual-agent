package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubAgentLogSummaryTest {
    @Test
    fun `summary includes status and empty placeholders`() {
        val agent =
            SubAgent(
                id = "agent-1",
                name = "researcher",
                role = "research",
                status = AgentStatus.IDLE,
                chatHistory = mutableListOf(),
            )

        val summary = subAgentLogSummary(agent, activeJobCount = 0)

        assertTrue(summary.contains("Status: IDLE"))
        assertTrue(summary.contains("Active jobs: 0"))
        assertTrue(summary.contains("Current task: None"))
        assertTrue(summary.contains("Current todo: None"))
        assertTrue(summary.contains("No recent chat history."))
    }

    @Test
    fun `summary includes recent chat history`() {
        val agent =
            SubAgent(
                id = "agent-1",
                name = "researcher",
                role = "research",
                status = AgentStatus.BUSY,
                currentTask = "find docs",
                currentTodoId = "todo-1",
                chatHistory = mutableListOf(Message(role = "user", content = "go"), Message(role = "assistant", content = "ok")),
            )

        val summary = subAgentLogSummary(agent, activeJobCount = 2)

        assertTrue(summary.contains("Status: BUSY"))
        assertTrue(summary.contains("Active jobs: 2"))
        assertTrue(summary.contains("Current task: find docs"))
        assertTrue(summary.contains("Current todo: todo-1"))
        assertTrue(summary.contains("1. USER"))
        assertTrue(summary.contains("2. ASSISTANT"))
    }

    @Test
    fun `summary truncates long content`() {
        val agent =
            SubAgent(
                id = "agent-1",
                name = "researcher",
                role = "research",
                status = AgentStatus.IDLE,
                chatHistory = mutableListOf(Message(role = "assistant", content = "x".repeat(1500))),
            )

        val summary = subAgentLogSummary(agent, activeJobCount = 0)

        assertEquals(1200, summary.lines().find { it.startsWith("x") }?.length)
    }
}
