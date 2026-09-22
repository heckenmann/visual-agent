package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.seedDefaultTestAgents
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactor.mono

/**
 * Shared instant-response fixture for terminal todo notification tests.
 */
internal fun createInstantTodoAgentManager(): TodoAgentManagerFixture {
    val db =
        de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
            .create("jdbc:h2:mem:test")
    val provider = mockk<LLMProvider>(relaxed = true)
    seedDefaultTestAgents(db)
    coEvery { provider.isConnected() } returns true
    every { provider.chatReactive(any<ChatRequestContext>()) } returns
        mono {
            ChatResponse(
                model = "test",
                message = Message("assistant", "Task completed"),
                done = true,
            )
        }
    return TodoAgentManagerFixture(
        manager =
            AgentManager(
                db,
                provider,
                AgentToolConfigService(db),
                ToolEventBus(),
                TodoEventBus(),
                AppConfigBean(db),
            ),
        provider = provider,
    )
}

/**
 * Test fixture exposing an agent manager and its provider spy.
 */
internal data class TodoAgentManagerFixture(
    val manager: AgentManager,
    val provider: LLMProvider,
)
