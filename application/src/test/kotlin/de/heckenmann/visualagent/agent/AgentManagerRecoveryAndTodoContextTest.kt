package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import reactor.core.publisher.Mono
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerRecoveryAndTodoContextTest {
    @Test
    fun `main request includes current todo list in system context`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            val requestSlot = slot<ChatRequestContext>()
            every { provider.chatReactive(capture(requestSlot)) } returns
                Mono.just(
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", "ok"),
                        done = true,
                    ),
                )
            val appConfig = AppConfigBean(db)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), appConfig)
            val previousInstruction = appConfig.userModelInstruction
            try {
                appConfig.userModelInstruction = "Always answer in German."
                manager.todoManager.add("Implement worker orchestration")

                manager.sendMessage("Start")

                val firstMessage = requestSlot.captured.messages.first()
                assertTrue(firstMessage.role == "system")
                assertTrue(firstMessage.content.contains("Current TODO list"))
                assertTrue(firstMessage.content.contains("Implement worker orchestration"))
                assertTrue(firstMessage.content.contains("Your Available Tools"))
                assertTrue(firstMessage.content.contains("do NOT have access to"))
                assertTrue(firstMessage.content.contains("agent:list"))
                assertTrue(firstMessage.content.contains("todos"))
                assertTrue(firstMessage.content.contains("Always answer in German."))
            } finally {
                appConfig.userModelInstruction = previousInstruction
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
            every { provider.checkConnectionReactive() } returns Mono.just(true)
            every { provider.chatReactive(any<ChatRequestContext>()) } returns
                Mono.just(
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", "Recovered and continued."),
                        done = true,
                    ),
                )
            AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            withTimeout(5_000) {
                while (db
                        .getConversationMessages(
                            "main",
                        ).none { it.role == "assistant" && it.content.contains("Recovered and continued.") }
                ) {
                    delay(50)
                }
            }
            val messages = db.getConversationMessages("main")
            assertTrue(messages.any { it.role == "assistant" && it.content.contains("Recovered and continued.") })
            verify(atLeast = 1) { provider.chatReactive(any<ChatRequestContext>()) }
        }

    @Test
    fun `manager records concise recovery failure when interrupted resume fails`() =
        runBlocking {
            val tempDb = createTempDirectory("visual-agent-resume-failure-test").resolve("resume.db").toString()
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create(tempDb)
            db.saveConversationMessage("main", "user", "Please continue after restart")

            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.checkConnectionReactive() } returns Mono.just(true)
            every { provider.chatReactive(any<ChatRequestContext>()) } returns
                Mono.error(IllegalStateException("401 invalid api key"))
            AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            withTimeout(5_000) {
                while (db.getConversationMessages("main").none {
                        it.role == "assistant" &&
                            it.content.contains("I could not resume the previous request automatically.")
                    }
                ) {
                    delay(50)
                }
            }
            val messages = db.getConversationMessages("main")
            assertTrue(
                messages.any {
                    it.role == "assistant" &&
                        it.content.contains("I could not resume the previous request automatically.") &&
                        it.content.contains("Authentication failed")
                },
            )
        }
}
