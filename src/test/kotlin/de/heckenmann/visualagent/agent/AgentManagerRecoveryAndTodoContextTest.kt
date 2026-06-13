package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class AgentManagerRecoveryAndTodoContextTest {
    @Test
    fun `main request includes current todo list in system context`() =
        runBlocking {
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
            val previousInstruction = AppConfig.instance.userModelInstruction
            try {
                AppConfig.instance.userModelInstruction = "Always answer in German."
                manager.todoManager.add("Implement worker orchestration")

                manager.sendMessage("Start")

                val firstMessage = requestSlot.captured.messages.first()
                val secondMessage = requestSlot.captured.messages[1]
                assertTrue(firstMessage.role == "system")
                assertTrue(firstMessage.content.contains("Current TODO list"))
                assertTrue(firstMessage.content.contains("Implement worker orchestration"))
                assertTrue(
                    firstMessage.content.contains(
                        "The main agent must not use direct workspace, file, terminal, browser, search, history, or todo tools",
                    ),
                )
                assertTrue(firstMessage.content.contains("Use only sub-agent control tools"))
                assertTrue(secondMessage.role == "system")
                assertTrue(secondMessage.content.contains("Always answer in German."))
            } finally {
                AppConfig.instance.userModelInstruction = previousInstruction
            }
        }

    @Test
    fun `manager resumes interrupted conversation when last message was from user`() =
        runBlocking {
            val tempDb = createTempDirectory("visual-agent-resume-test").resolve("resume.db").toString()
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create(tempDb)
            db.saveConversationMessage("main", "user", "Please continue after restart")

            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse(
                    model = "test",
                    message = Message("assistant", "Recovered and continued."),
                    done = true,
                )
            AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())

            delay(600)
            val messages = db.getConversationMessages("main")
            assertTrue(messages.any { it.role == "assistant" && it.content.contains("Recovered and continued.") })
            coVerify(atLeast = 1) { provider.chat(any<ChatRequestContext>()) }
        }
}
