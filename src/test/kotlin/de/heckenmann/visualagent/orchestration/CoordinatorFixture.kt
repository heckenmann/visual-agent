package de.heckenmann.visualagent.orchestration

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

/**
 * Test fixture for [AutonomousCoordinator] tests.
 *
 * @param coordinator The coordinator under test
 * @param todoManager Todo manager used by the coordinator
 * @param subAgents Sub-agent map
 * @param putSubAgent Function to add a sub-agent to the map
 * @param notifications Captured notification strings
 * @param savedAgents Captured saved agent instances
 * @param messages Captured persisted messages
 * @param scope Coroutine scope used by the fixture; cancel via [cancel]
 */
internal class CoordinatorFixture(
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

/**
 * Builds a [CoordinatorFixture] with configurable parameters.
 */
internal fun buildFixture(
    parallelism: Int = 4,
    chatDelayMs: Int = 0,
    responseContent: String = "APPROVED\nLooks good.",
    reviewContent: String = "APPROVED",
): CoordinatorFixture {
    val todoStore = FakeTodoStore()
    val todoEventBus = TodoEventBus()
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
        val ctx = it.invocation.args[0] as ChatRequestContext
        val token = ctx.cancellationToken
        val isReview = ctx.metadata["sessionId"] == "review"
        val content = if (isReview) reviewContent else responseContent
        if (chatDelayMs > 0 && !isReview) {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < chatDelayMs) {
                if (token?.isCancelled == true) throw kotlinx.coroutines.CancellationException("cancelled")
                kotlinx.coroutines.delay(50)
            }
        }
        ChatResponse(
            model = "test",
            message = Message("assistant", content),
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
    return CoordinatorFixture(coordinator, todoManager, subAgents, subAgentOps::putSubAgent, notifications, savedAgents, messages, scope)
}

internal class FakeTodoStore : TodoStore {
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
