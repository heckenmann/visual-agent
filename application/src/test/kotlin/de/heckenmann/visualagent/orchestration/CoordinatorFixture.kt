package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ParallelismProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentExecutionControl
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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
 * @param todoChanges Captured persisted todo change events
 * @param todoChangeSubscription Registration handle for the change listener
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
    val executionControl: SubAgentExecutionControl,
    val scheduler: SubAgentJobScheduler,
    val todoEventBus: TodoEventBus,
    val providerRequests: MutableList<ChatRequestContext>,
    private val workerStarts: Channel<Unit>,
    private val workerCompletions: Channel<Unit>,
    private val reviewStarts: Channel<Unit>,
    private val messageEvents: Channel<Message>,
    private val todoChanges: Channel<TodoChange>,
    private val todoChangeSubscription: AutoCloseable,
    private val scope: CoroutineScope,
) {
    fun cancel() {
        todoChangeSubscription.close()
        coordinator.close()
        scheduler.close()
        scope.cancel()
    }

    suspend fun awaitWorkerStart() {
        workerStarts.receive()
    }

    suspend fun awaitWorkerCompletion() {
        workerCompletions.receive()
    }

    suspend fun awaitReviewStart() {
        reviewStarts.receive()
    }

    suspend fun awaitTodoStatus(
        todoId: String,
        status: TodoStatus,
    ) {
        if (todoManager.getById(todoId)?.status == status) return
        while (true) {
            val changedTodo = todoChanges.receive().todo
            if (changedTodo?.id == todoId && changedTodo.status == status) return
        }
    }

    suspend fun awaitMessageContaining(text: String): Message {
        messages.firstOrNull { it.content.contains(text) }?.let { return it }
        while (true) {
            val message = messageEvents.receive()
            if (message.content.contains(text)) return message
        }
    }
}

/**
 * Builds a [CoordinatorFixture] with configurable parameters.
 */
internal fun buildFixture(
    parallelism: Int = 4,
    workerResponseGate: CompletableDeferred<Unit>? = null,
    responseContent: String = "APPROVED\nLooks good.",
    reviewContent: String = """{"verdict":"APPROVED","feedback":"Looks good."}""",
    failingWorkerAttempts: Int = 0,
    onWorkerStreamStarted: (() -> Unit)? = null,
    fixtureScope: CoroutineScope? = null,
    onPersistMessage: (Message) -> Unit = {},
    reviewResponseGate: CompletableDeferred<Unit>? = null,
    reviewResponses: List<String> = listOf(reviewContent),
    reviewFailure: Exception? = null,
): CoordinatorFixture {
    val todoStore = FakeTodoStore()
    val todoEventBus = TodoEventBus()
    val todoChanges = Channel<TodoChange>(Channel.UNLIMITED)
    val todoChangeSubscription = todoEventBus.addListener { change -> todoChanges.trySend(change) }
    val todoManager = TodoManager(todoStore, todoEventBus)
    val provider = mockk<LLMProvider>()
    val workerAttempts = AtomicInteger()
    val workerStarts = Channel<Unit>(Channel.UNLIMITED)
    val workerCompletions = Channel<Unit>(Channel.UNLIMITED)
    val reviewStarts = Channel<Unit>(Channel.UNLIMITED)
    val reviewAttempts = AtomicInteger()
    val providerRequests = CopyOnWriteArrayList<ChatRequestContext>()
    val messageEvents = Channel<Message>(Channel.UNLIMITED)
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
    every { provider.chatReactive(any<ChatRequestContext>()) } answers {
        val ctx = it.invocation.args[0] as ChatRequestContext
        val isReview = ctx.metadata["sessionId"] == "review"
        mono {
            providerRequests += ctx
            if (isReview) {
                reviewStarts.trySend(Unit)
                reviewResponseGate?.await()
                reviewFailure?.let { throw it }
            }
            if (!isReview) workerResponseGate?.await()
            ChatResponse(
                model = "test",
                message =
                    Message(
                        "assistant",
                        if (isReview) {
                            reviewResponses[
                                reviewAttempts.getAndIncrement().coerceAtMost(
                                    reviewResponses.lastIndex,
                                ),
                            ]
                        } else {
                            responseContent
                        },
                    ),
                done = true,
            )
        }
    }
    every { provider.streamReactive(any<ChatRequestContext>()) } answers {
        val ctx = it.invocation.args[0] as ChatRequestContext
        val isReview = ctx.metadata["sessionId"] == "review"
        flux {
            providerRequests += ctx
            if (!isReview && workerAttempts.incrementAndGet() <= failingWorkerAttempts) {
                throw IllegalStateException("transient worker failure")
            }
            if (!isReview) {
                workerStarts.trySend(Unit)
                onWorkerStreamStarted?.invoke()
                workerResponseGate?.await()
            }
            send(
                ChatResponse(
                    model = "test",
                    message = Message("assistant", if (isReview) reviewContent else responseContent),
                    done = true,
                ),
            )
            if (!isReview) workerCompletions.trySend(Unit)
        }
    }
    val notifications = CopyOnWriteArrayList<String>()
    val savedAgents = CopyOnWriteArrayList<SubAgent>()
    val messages = CopyOnWriteArrayList<Message>()
    val scope = fixtureScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val executionControl = SubAgentExecutionControl(FixturePreferenceStore())
    val parallelismProvider =
        object : ParallelismProvider() {
            override fun get(): Int = parallelism
        }
    val scheduler = SubAgentJobScheduler(scope, parallelismProvider, executionControl)
    val conversationOps =
        ConversationOpsProvider(mockk<ToolEventBus>(relaxed = true)).apply {
            setBeginConversationRequest {
                java.util.UUID
                    .randomUUID()
                    .toString()
            }
            setPersistMessage {
                onPersistMessage(it)
                messages.add(it)
                messageEvents.trySend(it)
                it
            }
            setBuildMainSystemContextPrompt { "You are the main orchestrator agent." }
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
            executionControl = executionControl,
            retryDelay = {},
        )
    return CoordinatorFixture(
        coordinator,
        todoManager,
        subAgents,
        subAgentOps::putSubAgent,
        notifications,
        savedAgents,
        messages,
        executionControl,
        scheduler,
        todoEventBus,
        providerRequests,
        workerStarts,
        workerCompletions,
        reviewStarts,
        messageEvents,
        todoChanges,
        todoChangeSubscription,
        scope,
    )
}

private class FixturePreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()

    override fun getPreference(key: String): String? = values[key]

    override fun setPreference(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
