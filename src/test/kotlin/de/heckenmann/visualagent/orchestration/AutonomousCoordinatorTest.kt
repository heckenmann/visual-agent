package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutonomousCoordinatorTest {
    @Test
    fun `assignNextTodo assigns pending todo to idle agent and schedules work`() =
        runBlocking {
            val fixture = coordinator()
            fixture.todoManager.add("Implement feature")
            fixture.subAgents["agent-1"] = SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE)

            try {
                val assigned = fixture.coordinator.assignNextTodo()

                assertTrue(assigned)
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
                delay(800)
                assertTrue(fixture.notifications.any { it.contains("STATUS:BUSY") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `assignNextTodo returns false when all agents are busy`() =
        runBlocking {
            val fixture = coordinator()
            fixture.todoManager.add("Implement feature")
            fixture.subAgents["agent-1"] = SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.BUSY)

            try {
                assertFalse(fixture.coordinator.assignNextTodo())
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `assignTodoToAgent assigns specific todo to specific agent`() =
        runBlocking {
            val fixture = coordinator()
            val todo = fixture.todoManager.add("Fix bug")
            fixture.subAgents["agent-1"] = SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE)

            try {
                val assigned = fixture.coordinator.assignTodoToAgent(todo.id, "agent-1")

                assertTrue(assigned)
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
                assertEquals(todo.id, fixture.subAgents["agent-1"]?.currentTodoId)
                delay(800)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `assignTodoToAgent returns false when agent is busy`() =
        runBlocking {
            val fixture = coordinator()
            val todo = fixture.todoManager.add("Fix bug")
            fixture.subAgents["agent-1"] = SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.BUSY)

            try {
                assertFalse(fixture.coordinator.assignTodoToAgent(todo.id, "agent-1"))
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `assignAllPendingTodos respects parallelism and idle agent count`() =
        runBlocking {
            val fixture = coordinator(parallelism = 2)
            fixture.todoManager.add("Task 1")
            fixture.todoManager.add("Task 2")
            fixture.todoManager.add("Task 3")
            fixture.subAgents["agent-1"] = SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE)
            fixture.subAgents["agent-2"] = SubAgent(id = "agent-2", name = "Tester", role = "Testing", status = AgentStatus.IDLE)
            fixture.subAgents["agent-3"] = SubAgent(id = "agent-3", name = "Reviewer", role = "Review", status = AgentStatus.BUSY)

            try {
                val count = fixture.coordinator.assignAllPendingTodos()

                assertEquals(2, count)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `seedUxTodos adds predefined seeds`() =
        runBlocking {
            val fixture = coordinator()

            try {
                fixture.coordinator.seedUxTodos()

                assertEquals(19, fixture.todoManager.getAll().size)
                assertTrue(fixture.todoManager.getAll().any { it.description.contains("command palette") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `startAutonomousMode adds goal without seeding defaults`() =
        runBlocking {
            val fixture = coordinator()

            try {
                fixture.coordinator.startAutonomousMode("Custom goal")

                assertEquals(1, fixture.todoManager.getAll().size)
                assertEquals(
                    "Custom goal",
                    fixture.todoManager
                        .getAll()
                        .single()
                        .description,
                )
            } finally {
                fixture.cancel()
            }
        }

    private class Fixture(
        val coordinator: AutonomousCoordinator,
        val todoManager: TodoManager,
        val subAgents: MutableMap<String, SubAgent>,
        val notifications: MutableList<String>,
        val savedAgents: MutableList<SubAgent>,
        private val scope: CoroutineScope,
    ) {
        fun cancel() {
            scope.cancel()
        }
    }

    private fun coordinator(parallelism: Int = 4): Fixture {
        val todoManager = TodoManager()
        val subAgents = mutableMapOf<String, SubAgent>()
        val provider = mockk<LLMProvider>()
        val todoStore = FakeTodoStore(todoManager)
        val memoryStore =
            object : MemoryStore {
                override fun saveMemory(
                    content: String,
                    tags: List<String>,
                ): String = "memory-1"

                override fun saveStructuredKnowledge(
                    subject: String,
                    summary: String,
                    nextSteps: String?,
                ): String = "knowledge-1"

                override fun searchMemories(
                    query: String,
                    limit: Int,
                ): List<de.heckenmann.visualagent.knowledge.Memory> = emptyList()
            }
        val toolConfig = mockk<AgentToolConfigService>()
        every { toolConfig.mainAgentTools() } returns emptySet()
        every { toolConfig.toolsFor(any<SubAgent>()) } returns emptySet()
        coEvery { provider.chat(any<ChatRequestContext>()) } returns
            ChatResponse(
                model = "test",
                message = Message("assistant", "APPROVED\nLooks good."),
                done = true,
            )
        val notifications = mutableListOf<String>()
        val savedAgents = mutableListOf<SubAgent>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduler = SubAgentJobScheduler(scope) { parallelism }
        val coordinator =
            AutonomousCoordinator(
                scope = scope,
                todoManager = todoManager,
                subAgents = subAgents,
                llmProvider = provider,
                todoStore = todoStore,
                memoryStore = memoryStore,
                agentToolConfigService = toolConfig,
                jobScheduler = scheduler,
                createAgent = { name, role, templateName ->
                    SubAgent
                        .fromTemplate(id = "created-${subAgents.size}", name = name, role = role, templateName = templateName)
                        .also { subAgents[it.id] = it }
                },
                saveAgentToDb = { savedAgents += it },
                notifyAgent = { agentId, message -> notifications += "$agentId:$message" },
            )
        return Fixture(coordinator, todoManager, subAgents, notifications, savedAgents, scope)
    }

    private class FakeTodoStore(
        private val todoManager: TodoManager,
    ) : TodoStore {
        override fun saveTodo(todo: Todo) {
        }

        override fun listTodos(): List<Todo> = todoManager.getAll()

        override fun deleteTodo(todoId: String) {
        }

        override fun clearTodos() {
        }
    }
}
