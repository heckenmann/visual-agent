package de.heckenmann.visualagent.agent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskWorkerPromptTest {
    @Test
    fun `disabled todo access is not advertised and custom role remains intact`() {
        val agent = SubAgent("worker", "Worker", "Antworte auf Deutsch und prüfe die Belege")
        val prompt = todoWorkerMessages(agent, "Custom task", emptySet()).first().content
        assertTrue(prompt.contains(agent.role))
        assertTrue(prompt.contains("worker/1"))
        assertTrue(prompt.contains("report a blocker"))
        assertFalse(prompt.contains("read-only `todos`"))
        val enabled = todoWorkerMessages(agent, "Custom task", setOf(ToolId("todos"))).first().content
        assertTrue(enabled.contains("read-only `todos`"))
    }
}
