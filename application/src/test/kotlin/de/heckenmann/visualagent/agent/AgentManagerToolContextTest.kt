package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemory
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryEdit
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryStore
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerToolContextTest {
    @Test
    fun `main agent only exposes sub-agent control tools`() =
        runTest {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
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
            assertTrue(ToolId("workspace:file") in requestSlot.captured.enabledTools)
            assertTrue(ToolId("memory") in requestSlot.captured.enabledTools)
            assertEquals("main", requestSlot.captured.metadata["agent"])
        }

    @Test
    fun `main request injects the current durable memory once before history`() =
        runTest {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            val requestSlot = slot<ChatRequestContext>()
            coEvery { provider.chat(capture(requestSlot)) } returns ChatResponse("test", Message("assistant", "ok"), true)
            val memory = RecordingMemoryStore("Remember durable project constraints.")
            val manager =
                AgentManager(
                    db,
                    provider,
                    AgentToolConfigService(db),
                    ToolEventBus(),
                    TodoEventBus(),
                    AppConfigBean(db),
                    mainAgentLongTermMemoryStore = memory,
                )

            manager.sendMessage("Hello")

            val memoryMessages = requestSlot.captured.messages.filter { it.content.contains("Durable Main-Agent Memory") }
            assertEquals(1, memoryMessages.size)
            assertTrue(memoryMessages.single().content.contains("Remember durable project constraints."))
            assertTrue(
                memoryMessages
                    .single()
                    .content
                    .contains("do not create, update, or delegate a todo merely to use memory"),
            )
            assertTrue(
                requestSlot.captured.messages.indexOf(memoryMessages.single()) <
                    requestSlot.captured.messages.indexOfLast { it.role == "user" },
            )
            assertEquals(1, memory.snapshotCalls)
        }

    @Test
    fun `memory section reports when the memory tool is globally disabled`() =
        runTest {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val tools = AgentToolConfigService(db)
            tools.setToolGloballyEnabled("memory", enabled = false)
            val provider = mockk<LLMProvider>(relaxed = true)
            val requestSlot = slot<ChatRequestContext>()
            coEvery { provider.chat(capture(requestSlot)) } returns ChatResponse("test", Message("assistant", "ok"), true)
            val manager =
                AgentManager(
                    db,
                    provider,
                    tools,
                    ToolEventBus(),
                    TodoEventBus(),
                    AppConfigBean(db),
                    mainAgentLongTermMemoryStore = RecordingMemoryStore("read only"),
                )

            manager.sendMessage("Hello")

            assertFalse(ToolId("memory") in requestSlot.captured.enabledTools)
            assertTrue(requestSlot.captured.messages.any { it.content.contains("currently unavailable") })
        }

    private class RecordingMemoryStore(
        content: String,
    ) : MainAgentLongTermMemoryStore {
        private val memory = MainAgentLongTermMemory(content, content.length, 3, Instant.EPOCH)
        var snapshotCalls = 0

        override fun snapshot(): MainAgentLongTermMemory {
            snapshotCalls += 1
            return memory
        }

        override fun replace(
            content: String,
            expectedRevision: Long,
            maxCodePoints: Int,
        ): MainAgentLongTermMemoryEdit = MainAgentLongTermMemoryEdit.Conflict(memory)
    }
}
