package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentManagerToolContextTest {
    @Test
    fun `main agent passes enabled tools through provider interface`() =
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
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())

            manager.sendMessage("Use a tool")

            assertTrue(ToolId("ui") in requestSlot.captured.enabledTools)
            assertTrue(ToolId("file:read") in requestSlot.captured.enabledTools)
            assertTrue(ToolId("terminal") in requestSlot.captured.enabledTools)
            assertTrue(ToolId("history") in requestSlot.captured.enabledTools)
            assertEquals("main", requestSlot.captured.metadata["agent"])
        }
}
