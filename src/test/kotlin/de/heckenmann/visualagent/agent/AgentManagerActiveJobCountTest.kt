package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfig
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

class AgentManagerActiveJobCountTest {
    @Test
    fun `active job count tracks concurrent executions for the same agent`() =
        runBlocking {
            val originalParallelism = AppConfig.instance.maxParallelSubAgents
            val stores =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
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
                AgentManager(stores, provider, AgentToolConfigService(stores), ToolEventBus(), TodoEventBus(), AppConfigBean(stores))

            try {
                AppConfig.instance.maxParallelSubAgents = 2
                val first = async { manager.runAgentJob("1", "first") }
                val second = async { manager.runAgentJob("1", "second") }

                bothStarted.await()
                assertEquals(2, manager.getActiveJobCount("1"))
                assertEquals(AgentStatus.BUSY, manager.getSubAgent("1")?.status)

                release.complete(Unit)
                first.await()
                second.await()
                assertEquals(0, manager.getActiveJobCount("1"))
                assertEquals(AgentStatus.IDLE, manager.getSubAgent("1")?.status)
            } finally {
                AppConfig.instance.maxParallelSubAgents = originalParallelism
                manager.destroy()
                stores.close()
            }
        }
}
