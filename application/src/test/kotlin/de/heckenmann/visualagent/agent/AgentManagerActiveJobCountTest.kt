package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerActiveJobCountTest {
    @Test
    fun `active job count tracks concurrent executions for the same agent`() =
        runBlocking {
            val config = AppConfigBean()
            val stores =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            val startedCount = AtomicInteger()
            val bothStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            coEvery { provider.chat(any<ChatRequestContext>()) } coAnswers {
                if (startedCount.incrementAndGet() == 2) bothStarted.complete(Unit)
                release.await()
                ChatResponse("test", Message("assistant", "completed"), done = true)
            }
            val manager =
                AgentManager(stores, provider, AgentToolConfigService(stores), ToolEventBus(), TodoEventBus(), config)

            try {
                config.maxParallelSubAgents = 2
                val agent = manager.createAgent("Worker", "Concurrent test worker")
                val first = async { manager.runAgentJob(agent.id, "first") }
                val second = async { manager.runAgentJob(agent.id, "second") }

                bothStarted.await()
                assertEquals(2, manager.getActiveJobCount(agent.id))
                assertEquals(AgentStatus.BUSY, manager.getSubAgent(agent.id)?.status)

                release.complete(Unit)
                first.await()
                second.await()
                assertEquals(0, manager.getActiveJobCount(agent.id))
                assertEquals(AgentStatus.IDLE, manager.getSubAgent(agent.id)?.status)
            } finally {
                config.maxParallelSubAgents = 4
                manager.destroy()
                stores.close()
            }
        }
}
