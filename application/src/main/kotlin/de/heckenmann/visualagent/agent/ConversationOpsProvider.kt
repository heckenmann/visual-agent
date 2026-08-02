package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides conversation operations for coordinator beans, breaking the circular
 * dependency between [AgentManager] and the coordinators.
 *
 * [AgentManager] wires the lambdas via [setBuildMainRequest], [setBuildMainSystemContextPrompt],
 * [setLoadRecentHistoryFromDb], and [setPersistMessage] during its `init` block.
 */
class ConversationOpsProvider(
    private val toolEventBus: ToolEventBus,
) {
    val finishedToolEventsByRequestId = ConcurrentHashMap<String, MutableList<ToolCallEvent>>()

    private var buildMainRequest: ((List<Message>, String?, CancellationToken?) -> ChatRequestContext)? = null
    private var buildMainSystemContextPrompt: (() -> String)? = null
    private var loadRecentHistoryFromDb: ((Int) -> List<Message>)? = null
    private var persistMessage: ((Message) -> Message)? = null

    /**
     * Sets the lambda for building a main-agent chat request.
     */
    fun setBuildMainRequest(fn: (List<Message>, String?, CancellationToken?) -> ChatRequestContext) {
        buildMainRequest = fn
    }

    /**
     * Sets the lambda for building the main-agent system context prompt.
     */
    fun setBuildMainSystemContextPrompt(fn: () -> String) {
        buildMainSystemContextPrompt = fn
    }

    /**
     * Sets the lambda for loading recent conversation history from the database.
     */
    fun setLoadRecentHistoryFromDb(fn: (Int) -> List<Message>) {
        loadRecentHistoryFromDb = fn
    }

    /**
     * Sets the lambda for persisting a message to the database.
     */
    fun setPersistMessage(fn: (Message) -> Message) {
        persistMessage = fn
    }

    /**
     * Builds a main-agent chat request from the given history, optional request ID, and cancellation token.
     */
    fun buildMainRequest(
        history: List<Message>,
        requestId: String? = null,
        token: CancellationToken? = null,
    ): ChatRequestContext =
        checkNotNull(buildMainRequest) { "buildMainRequest not wired; ensure AgentManager.init completed" }(history, requestId, token)

    /**
     * Builds the main-agent system context prompt string.
     */
    fun buildMainSystemContextPrompt(): String =
        checkNotNull(buildMainSystemContextPrompt) { "buildMainSystemContextPrompt not wired; ensure AgentManager.init completed" }()

    /**
     * Loads recent conversation history from the database, up to [limit] messages.
     */
    fun loadRecentHistoryFromDb(limit: Int = 20): List<Message> =
        checkNotNull(loadRecentHistoryFromDb) { "loadRecentHistoryFromDb not wired; ensure AgentManager.init completed" }(limit)

    /**
     * Persists a message to the database and returns the saved message.
     */
    fun persist(message: Message): Message =
        checkNotNull(persistMessage) { "persistMessage not wired; ensure AgentManager.init completed" }(message)

    /**
     * Registers a listener on the [ToolEventBus] that collects finished tool-call events
     * keyed by request ID. Returns an [AutoCloseable] handle to remove the listener.
     */
    fun registerToolEventListener(): AutoCloseable =
        toolEventBus.addListener { event ->
            if (event.phase != ToolCallPhase.FINISHED) return@addListener
            val requestId = event.context["requestId"]?.toString().orEmpty()
            if (requestId.isBlank()) return@addListener
            finishedToolEventsByRequestId.computeIfAbsent(requestId) { mutableListOf() }.add(event)
        }
}
