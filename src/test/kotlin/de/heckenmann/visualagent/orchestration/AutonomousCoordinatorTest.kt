package de.heckenmann.visualagent.orchestration
// TODO(size): 312 effective LOC, needs splitting

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ParallelismProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
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
import kotlin.test.assertTrue

class AutonomousCoordinatorTest {
    @Test
    fun `auto pickup assigns pending todo to idle agent and schedules work`(): Unit =
        runBlocking {
            val fixture = coordinator(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.todoManager.add("Implement feature", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(2000)

                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
                assertTrue(fixture.notifications.any { it.contains("STATUS:BUSY") })
                assertTrue(fixture.messages.any { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `auto pickup auto assigns unassigned todo to idle agent`(): Unit =
        runBlocking {
            val fixture = coordinator(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Implement feature")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(2000)

                assertEquals("agent-1", todo.assignedAgentId)
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
                assertTrue(fixture.messages.any { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `auto pickup does nothing when all agents are busy`() =
        runBlocking {
            val fixture = coordinator()
            fixture.todoManager.add("Implement feature", "agent-1")
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.BUSY))

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(800)

                assertTrue(fixture.messages.none { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `auto pickup skips todo assigned to missing agent`() =
        runBlocking {
            val fixture = coordinator()
            fixture.todoManager.add("Implement feature", "agent-missing")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(800)

                assertTrue(fixture.messages.none { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `auto pickup respects parallelism limit`() =
        runBlocking {
            val fixture = coordinator(parallelism = 1, chatDelayMs = 5000)
            fixture.todoManager.add("Task 1", "agent-1")
            fixture.todoManager.add("Task 2", "agent-2")
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.putSubAgent(SubAgent(id = "agent-2", name = "Tester", role = "Testing", status = AgentStatus.IDLE))

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(1200)

                assertEquals(1, fixture.subAgents.values.count { it.status == AgentStatus.BUSY })
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

    @Test
    fun `completion message contains only todo id and get-result hint`() =
        runBlocking {
            val fixture = coordinator()
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.todoManager.add("Implement feature", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(1000)

                val completion = fixture.messages.firstOrNull { it.content.contains("completed todo") }
                requireNotNull(completion)
                assertTrue(completion.content.contains("Use `todos` with `get-result`"))
                assertTrue(completion.content.contains("completed todo"))
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `sub-agent restarts when todo description is edited while running`() =
        runBlocking {
            val fixture = coordinator(chatDelayMs = 1000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Old description", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(400)
                fixture.todoManager.update(todo.id, "New description")

                delay(4000)

                assertTrue(fixture.messages.any { it.content.contains("Todo ${todo.id} was updated") })
                assertTrue(fixture.messages.any { it.content.contains("completed todo ${todo.id}") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `coordinator resumes loop when existing todo is reset to PENDING`() =
        runBlocking {
            val fixture = coordinator(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Implement feature", "agent-1")
            fixture.todoManager.cancelTodo(todo.id)

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(400)
                assertTrue(fixture.subAgents["agent-1"]?.status == AgentStatus.IDLE)

                fixture.todoManager.updateStatus(todo.id, de.heckenmann.visualagent.todo.TodoStatus.PENDING)
                delay(2000)

                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
                assertTrue(fixture.messages.any { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `sub-agent stops when assigned agent changes while running`() =
        runBlocking {
            val fixture = coordinator(chatDelayMs = 1000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.putSubAgent(SubAgent(id = "agent-2", name = "Tester", role = "Testing", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Task", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(400)
                fixture.todoManager.updateAssignedAgent(todo.id, "agent-2")

                delay(2500)

                assertTrue(fixture.messages.any { it.content.contains("Stopped because the todo was cancelled, deleted, or reassigned") })
            } finally {
                fixture.cancel()
            }
        }

    private class Fixture(
        val coordinator: AutonomousCoordinator,
        val todoManager: TodoManager,
        val subAgents: Map<String, SubAgent>,
        val putSubAgent: (SubAgent) -> Unit,
        val notifications: MutableList<String>,
        val savedAgents: MutableList<SubAgent>,
        val messages: MutableList<Message>,
        private val scope: CoroutineScope,
    ) {
        fun cancel() {
            scope.cancel()
        }
    }

    private fun coordinator(
        parallelism: Int = 4,
        chatDelayMs: Long = 0,
    ): Fixture {
        val todoEventBus = TodoEventBus()
        val todoStore = FakeTodoStore()
        val todoManager = TodoManager(todoStore, todoEventBus)
        val provider = mockk<LLMProvider>()
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
        coEvery { provider.chat(any<ChatRequestContext>()) } coAnswers {
            val token = it.invocation.args[0].let { arg -> (arg as ChatRequestContext).cancellationToken }
            if (chatDelayMs > 0) {
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < chatDelayMs) {
                    if (token?.isCancelled == true) throw kotlinx.coroutines.CancellationException("cancelled")
                    delay(50)
                }
            }
            ChatResponse(
                model = "test",
                message = Message("assistant", "APPROVED\nLooks good."),
                done = true,
            )
        }
        val notifications = mutableListOf<String>()
        val savedAgents = mutableListOf<SubAgent>()
        val messages = mutableListOf<Message>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val parallelismProvider =
            object : ParallelismProvider() {
                override fun get(): Int = parallelism
            }
        val scheduler = SubAgentJobScheduler(scope, parallelismProvider)
        val conversationOps =
            ConversationOpsProvider(mockk<ToolEventBus>(relaxed = true)).apply {
                setPersistMessage {
                    messages.add(it)
                    it
                }
            }
        val subAgentOps = SubAgentOpsProvider()
        subAgentOps.setCreateAgent { name, role, templateName ->
            SubAgent
                .fromTemplate(id = "created-${subAgentOps.allSubAgents.size}", name = name, role = role, templateName = templateName)
                .also { subAgentOps.putSubAgent(it) }
        }
        subAgentOps.setSaveSubAgent { savedAgents.add(it) }
        subAgentOps.setNotifyAgent { agentId, message -> notifications += "$agentId:$message" }
        val subAgents = subAgentOps.allSubAgents
        val coordinator =
            AutonomousCoordinator(
                scope = scope,
                todoManager = todoManager,
                llmProvider = provider,
                todoStore = todoStore,
                memoryStore = memoryStore,
                agentToolConfigService = toolConfig,
                jobScheduler = scheduler,
                parallelismProvider = parallelismProvider,
                todoEventBus = todoEventBus,
                conversationOps = conversationOps,
                subAgentOps = subAgentOps,
            )
        return Fixture(coordinator, todoManager, subAgents, subAgentOps::putSubAgent, notifications, savedAgents, messages, scope)
    }

    private class FakeTodoStore : TodoStore {
        private val todos = mutableListOf<Todo>()

        override fun saveTodo(todo: Todo) {
            todos.removeIf { it.id == todo.id }
            todos.add(todo)
        }

        override fun listTodos(): List<Todo> = todos.toList()

        override fun deleteTodo(todoId: String) {
            todos.removeIf { it.id == todoId }
        }

        override fun clearTodos() {
            todos.clear()
        }
    }
}
