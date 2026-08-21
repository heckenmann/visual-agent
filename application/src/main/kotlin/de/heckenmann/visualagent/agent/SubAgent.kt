package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.conversation.appendStreamPart
import de.heckenmann.visualagent.knowledge.MemoryStore
import kotlinx.coroutines.flow.collect
import mu.KotlinLogging

/**
 * Runtime availability shown in the sub-agent list and used for scheduling.
 */
enum class AgentStatus {
    IDLE,
    BUSY,
    OFFLINE,
}

/**
 * Configurable worker agent that can receive chat turns and execute assigned todos.
 *
 * @property id Stable agent identifier
 * @property name User-visible agent name
 * @property role Role prompt and UI description
 * @property status Current scheduling state
 * @property currentTask Optional human-readable task currently being executed
 * @property currentTodoId Optional todo identifier currently assigned to the agent
 * @property parentAgentId Parent agent that spawned this agent, if any
 * @property chatHistory Recent per-agent conversation history
 * @property config Runtime, model, and tool-related configuration
 * @property createdAt Creation timestamp in epoch milliseconds
 * @property updatedAt Last update timestamp in epoch milliseconds
 */
data class SubAgent(
    val id: String,
    var name: String,
    var role: String,
    var status: AgentStatus = AgentStatus.IDLE,
    var currentTask: String? = null,
    var currentTodoId: String? = null,
    val parentAgentId: String? = null,
    var chatHistory: MutableList<Message> = mutableListOf(),
    var config: AgentConfig = AgentConfig(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Returns true when the other object is a [SubAgent] with the same identity and mutable state.
     * Timestamps are intentionally excluded so equality reflects business state, not creation time.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SubAgent) return false
        return id == other.id &&
            name == other.name &&
            role == other.role &&
            status == other.status &&
            currentTask == other.currentTask &&
            currentTodoId == other.currentTodoId &&
            parentAgentId == other.parentAgentId &&
            chatHistory == other.chatHistory &&
            config == other.config
    }

    /**
     * Hash code consistent with [equals]; timestamps are excluded.
     */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (currentTask?.hashCode() ?: 0)
        result = 31 * result + (currentTodoId?.hashCode() ?: 0)
        result = 31 * result + (parentAgentId?.hashCode() ?: 0)
        result = 31 * result + chatHistory.hashCode()
        result = 31 * result + config.hashCode()
        return result
    }

    companion object {
        /**
         * Creates a sub-agent with configuration loaded from a named template.
         *
         * @param id Stable agent identifier
         * @param name User-visible agent name
         * @param role Role prompt and UI description
         * @param templateName Template key used to initialize [AgentConfig]
         * @return New sub-agent instance
         */
        fun fromTemplate(
            id: String,
            name: String,
            role: String,
            templateName: String,
        ): SubAgent {
            val config = AgentConfig.fromTemplate(templateName)
            return SubAgent(
                id = id,
                name = name,
                role = role,
                config = config,
            )
        }
    }

    /**
     * Send messages to this sub-agent using the provided LLM provider.
     *
     * @param messages New messages for this turn
     * @param provider Provider used for the model call
     * @param enabledTools Tool IDs exposed to this sub-agent
     * @return Assistant response
     */
    suspend fun chat(
        messages: List<Message>,
        provider: LLMProvider,
        enabledTools: Set<ToolId> = emptySet(),
        token: CancellationToken? = null,
    ): ChatResponse {
        val response = provider.chat(buildRequest(messages, enabledTools, token))
        appendChatHistory(messages, response)
        return response
    }

    /**
     * Perform a todo autonomously: call the LLM and persist its result in the knowledge DB.
     * A persistence failure aborts the operation so the caller cannot mark the todo complete
     * without a retrievable result.
     * The caller should set status/assignment before invoking this.
     *
     * @param todoId Todo identifier used for memory storage
     * @param description Task description shown to the sub-agent
     * @param provider LLM provider used for the call
     * @param memoryStore Knowledge store for result and agent log persistence
     * @param enabledTools Tool IDs exposed to this sub-agent
     * @param token Optional cancellation token honoured during the LLM call
     * @param onChunk Optional callback for streamed response deltas
     * @return Assistant response content
     */
    suspend fun performTodo(
        todoId: String,
        description: String,
        provider: LLMProvider,
        memoryStore: MemoryStore,
        enabledTools: Set<ToolId> = emptySet(),
        token: CancellationToken? = null,
        onChunk: ((String) -> Unit)? = null,
    ): String {
        val messages =
            listOf(
                Message(
                    "system",
                    buildString {
                        append("You are $name. Your role is $role.")
                        append(" The main agent and orchestrator control the todo lifecycle.")
                        append(
                            " You may inspect todos and stored results, but do not add, update, complete, " +
                                "cancel, start, stop, remove, or reorder todos.",
                        )
                        append(" If the task becomes unclear, use the read-only `todos` actions to re-read the current description.")
                        append(
                            " Report a concise result and next steps; the orchestrator persists the result " +
                                "and decides the final status.",
                        )
                    },
                ),
                Message("user", description),
            )

        val resp = responseForTodo(messages, provider, enabledTools, token, onChunk)

        val summary =
            resp.message.content
                .trim()
                .take(1000)
                .ifBlank { "(No text response; inspect the persisted tool results.)" }
        try {
            val nextSteps = "Review and implement improvements as needed."
            memoryStore.saveStructuredKnowledge(subject = "todo:$todoId", summary = summary, nextSteps = nextSteps)
        } catch (error: Exception) {
            logger.error(error) { "Failed to persist result for todo $todoId" }
            throw IllegalStateException("Failed to persist result for todo $todoId", error)
        }
        runCatching {
            memoryStore.saveStructuredKnowledge(
                subject = "agent:$id:log",
                summary = "Worked on todo $todoId: ${description.take(120)}",
                nextSteps = summary,
            )
        }.onFailure { error ->
            logger.warn(error) { "Failed to persist agent log for todo $todoId" }
        }
        return resp.message.content
    }

    private suspend fun responseForTodo(
        messages: List<Message>,
        provider: LLMProvider,
        enabledTools: Set<ToolId>,
        token: CancellationToken?,
        onChunk: ((String) -> Unit)?,
    ): ChatResponse {
        if (onChunk == null) return chat(messages, provider, enabledTools, token)
        return try {
            stream(messages, provider, enabledTools, token, onChunk)
        } catch (error: Exception) {
            if (!isStreamingUnavailable(error)) throw error
            logger.info { "Streaming is unavailable for sub-agent $id; using a complete response instead" }
            chat(messages, provider, enabledTools, token)
        }
    }

    private suspend fun stream(
        messages: List<Message>,
        provider: LLMProvider,
        enabledTools: Set<ToolId>,
        token: CancellationToken?,
        onChunk: (String) -> Unit,
    ): ChatResponse {
        val collected = StringBuilder()
        var terminalResponse: ChatResponse? = null
        provider.stream(buildRequest(messages, enabledTools, token)).collect { chunk ->
            token?.throwIfCancelled()
            if (chunk.done) terminalResponse = chunk
            val part = chunk.message.content
            if (part.isNotEmpty()) {
                onChunk(appendStreamPart(collected, part))
            }
        }
        val response = terminalResponse ?: throw IllegalStateException("stream returned no terminal response")
        val completeResponse = response.copy(message = Message("assistant", collected.toString()), done = true)
        appendChatHistory(messages, completeResponse)
        return completeResponse
    }

    private fun buildRequest(
        messages: List<Message>,
        enabledTools: Set<ToolId>,
        token: CancellationToken?,
    ): ChatRequestContext {
        val modelSelection = config.modelSelection()
        return ChatRequestContext(
            messages = chatHistory + messages,
            provider = modelSelection.provider,
            model = modelSelection.model,
            variant = modelSelection.variant,
            parameters = modelSelection.parameters,
            options = modelSelection.options,
            enabledTools = enabledTools,
            metadata = mapOf("agentId" to id, "agentName" to name, "agentRole" to role),
            cancellationToken = token,
        )
    }

    private fun appendChatHistory(
        messages: List<Message>,
        response: ChatResponse,
    ) {
        chatHistory.add(Message("user", "Please complete the following task:\n${messages.joinToString("\n") { it.content }}"))
        chatHistory.add(response.message)
    }

    private fun isStreamingUnavailable(error: Exception): Boolean =
        error is UnsupportedOperationException ||
            (error is IllegalStateException && error.message?.contains("stream", ignoreCase = true) == true)
}
