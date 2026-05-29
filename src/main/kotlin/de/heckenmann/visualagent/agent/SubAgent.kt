package de.heckenmann.visualagent.agent

enum class AgentStatus {
    IDLE,
    BUSY,
    OFFLINE,
}

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
    companion object {
        /**
         * Create a SubAgent from a template name.
         */
        fun fromTemplate(id: String, name: String, role: String, templateName: String): SubAgent {
            val config = AgentConfig.fromTemplate(templateName)
            return SubAgent(
                id = id,
                name = name,
                role = role,
                config = config,
            )
        }
    }
    suspend fun chat(messages: List<Message>, provider: LLMProvider): ChatResponse {
        val combined = chatHistory + messages
        val response = provider.chat(combined)
        // Save a brief record of the task and the assistant response in the agent's chat history.
        chatHistory.add(Message("user", "Please complete the following task:\n${messages.joinToString("\n") { it.content }}"))
        chatHistory.add(response.message)
        return response
    }

    /**
     * Perform a todo autonomously: call the LLM, and write a result to the knowledge DB if available.
     * The caller should set status/assignment before invoking this.
     */
    suspend fun performTodo(todoId: String, description: String, provider: LLMProvider, knowledgeDb: de.heckenmann.visualagent.knowledge.KnowledgeDb) {
        val messages = listOf(
            Message("system", "You are ${name}. Your role is ${role}. Perform the following task and provide a concise result and the next steps."),
            Message("user", description)
        )

        val resp = chat(messages, provider)

        // Persist result summary in knowledge DB (best-effort)
        try {
            // Persist a structured knowledge record: summary + simple next steps (best-effort)
            val summary = resp.message.content.take(1000)
            val nextSteps = "Review and implement improvements as needed."
            knowledgeDb.saveStructuredKnowledge(subject = "todo:$todoId", summary = summary, nextSteps = nextSteps)
        } catch (e: Exception) {
            // swallow persistence errors to avoid blocking agent progress
        }
    }
}
