package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentManagerToolPayloadNormalizationTest {
    @Test
    fun `sendMessage normalizes empty tool_calls payload to tool-only placeholder`() {
        val tempDb = createTempDirectory("visual-agent-tool-payload-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider = mockk<LLMProvider>(relaxed = true)
        coEvery { provider.chat(any<ChatRequestContext>()) } returns
            ChatResponse(
                model = "test",
                message = Message("assistant", """{"tool_calls": []}"""),
                done = true,
            )
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

        val response =
            runBlocking {
                manager.sendMessage("Bitte rufe ein Tool auf")
            }

        assertEquals("(No text response. See tool results above.)", response)
        val last = manager.getHistory().last()
        assertEquals("(No text response. See tool results above.)", last.content)
        db.close()
    }
}
