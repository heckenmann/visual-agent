package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentManagerToolContextTest {
    @Test
    fun `main agent only exposes sub-agent control tools`() =
        runTest {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            val requestSlot = slot<ChatRequestContext>()
            coEvery { provider.chat(capture(requestSlot)) } returns
                ChatResponse(
                    model = "test",
                    message = Message("assistant", "ok"),
                    done = true,
                )
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.sendMessage("Use a tool")

            assertTrue(ToolId("agent:list") in requestSlot.captured.enabledTools)
            assertTrue(ToolId("agent:create") in requestSlot.captured.enabledTools)
            assertTrue(ToolId("agent:update") in requestSlot.captured.enabledTools)
            assertTrue(ToolId("agent:delete") in requestSlot.captured.enabledTools)
            assertFalse(ToolId("agent:start") in requestSlot.captured.enabledTools)
            assertFalse(ToolId("agent:assign-todo") in requestSlot.captured.enabledTools)
            assertFalse(ToolId("agent:message") in requestSlot.captured.enabledTools)
            assertFalse(ToolId("ui") in requestSlot.captured.enabledTools)
            assertFalse(ToolId("file:read") in requestSlot.captured.enabledTools)
            assertFalse(ToolId("terminal") in requestSlot.captured.enabledTools)
            assertFalse(ToolId("history") in requestSlot.captured.enabledTools)
            assertFalse(ToolId("workspace:layout") in requestSlot.captured.enabledTools)
            assertFalse(ToolId("canvas") in requestSlot.captured.enabledTools)
            assertEquals("main", requestSlot.captured.metadata["agent"])
        }
}
