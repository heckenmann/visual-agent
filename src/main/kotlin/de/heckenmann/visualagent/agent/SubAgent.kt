package de.heckenmann.visualagent.agent

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
        val combined = chatHistory + messages
        val modelSelection = config.modelSelection()
        val response =
            provider.chat(
                ChatRequestContext(
                    messages = combined,
                    provider = modelSelection.provider,
                    model = modelSelection.model,
                    variant = modelSelection.variant,
                    parameters = modelSelection.parameters,
                    options = modelSelection.options,
                    enabledTools = enabledTools,
                    metadata = mapOf("agentId" to id, "agentName" to name, "agentRole" to role),
                    cancellationToken = token,
                ),
            )
        // Save a brief record of the task and the assistant response in the agent's chat history.
        chatHistory.add(Message("user", "Please complete the following task:\n${messages.joinToString("\n") { it.content }}"))
        chatHistory.add(response.message)
        return response
    }

    /**
     * Perform a todo autonomously: call the LLM, and write a result to the knowledge DB if available.
     * The caller should set status/assignment before invoking this.
     *
     * @param todoId Todo identifier used for memory storage
     * @param description Task description shown to the sub-agent
     * @param provider LLM provider used for the call
     * @param memoryStore Knowledge store for result and agent log persistence
     * @param enabledTools Tool IDs exposed to this sub-agent
     * @param token Optional cancellation token honoured during the LLM call
     * @return Assistant response content
     */
    suspend fun performTodo(
        todoId: String,
        description: String,
        provider: LLMProvider,
        memoryStore: de.heckenmann.visualagent.knowledge.MemoryStore,
        enabledTools: Set<ToolId> = emptySet(),
        token: CancellationToken? = null,
    ): String {
        val messages =
            listOf(
                Message(
                    "system",
                    buildString {
                        append("You are $name. Your role is $role.")
                        append(" The todo you are working on may be edited or cancelled while you work.")
                        append(" If the task becomes unclear, use the `todos` tool to re-read the current description.")
                        append(" Provide a concise result and next steps.")
                    },
                ),
                Message("user", description),
            )

        val resp = chat(messages, provider, enabledTools, token)

        // Persist result summary in knowledge DB (best-effort)
        try {
            val summary = resp.message.content.take(1000)
            val nextSteps = "Review and implement improvements as needed."
            memoryStore.saveStructuredKnowledge(subject = "todo:$todoId", summary = summary, nextSteps = nextSteps)
            memoryStore.saveStructuredKnowledge(
                subject = "agent:$id:log",
                summary = "Worked on todo $todoId: ${description.take(120)}",
                nextSteps = summary,
            )
        } catch (e: Exception) {
            // swallow persistence errors to avoid blocking agent progress
        }
        return resp.message.content
    }
}
