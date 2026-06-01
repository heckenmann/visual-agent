package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.SubAgentToolConfig
import de.heckenmann.visualagent.todo.Todo
import org.springframework.stereotype.Repository

/**
 * Represents KnowledgeDb.
 */
@Repository
class KnowledgeDb(
    knowledgeSchema: KnowledgeSchema,
    private val memoryDao: MemoryDao,
    private val preferenceDao: PreferenceDao,
    private val conversationDao: ConversationDao,
    private val todoDao: TodoDao,
    private val agentDao: SubAgentDao,
    private val configDao: SubAgentConfigDao,
) {
    init {
        knowledgeSchema.initDatabase()
    }

    /**
     * Executes saveMemory.
     */
    fun saveMemory(
        content: String,
        tags: List<String> = emptyList(),
    ): String = memoryDao.saveMemory(content, tags)

    /**
     * Executes saveStructuredKnowledge.
     */
    fun saveStructuredKnowledge(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): String = memoryDao.saveStructuredKnowledge(subject, summary, nextSteps)

    /**
     * Executes searchMemories.
     */
    fun searchMemories(
        query: String,
        limit: Int = 10,
    ): List<Memory> = memoryDao.searchMemories(query, limit)

    /**
     * Executes getPreference.
     */
    fun getPreference(key: String): String? = preferenceDao.getPreference(key)

    /**
     * Executes setPreference.
     */
    fun setPreference(
        key: String,
        value: String,
    ) = preferenceDao.setPreference(key, value)

    /**
     * Executes saveConversationMessage.
     */
    fun saveConversationMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
    ): String = conversationDao.saveConversationMessage(sessionId, role, content, metadata)

    /**
     * Executes getConversationMessages.
     */
    fun getConversationMessages(
        sessionId: String,
        limit: Int = 500,
    ): List<Map<String, String>> = conversationDao.getConversationMessages(sessionId, limit)

    /**
     * Executes getConversationMessagesPage.
     */
    fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<Map<String, String>> = conversationDao.getConversationMessagesPage(sessionId, limit, offset)

    /**
     * Executes searchConversationMessages.
     */
    fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int = 20,
    ): List<Map<String, String>> = conversationDao.searchConversationMessages(sessionId, query, limit)

    /**
     * Executes deleteConversationMessages.
     */
    fun deleteConversationMessages(sessionId: String): Int = conversationDao.deleteConversationMessages(sessionId)

    /**
     * Executes saveTodo.
     */
    fun saveTodo(todo: Todo) = todoDao.saveTodo(todo)

    /**
     * Executes listTodos.
     */
    fun listTodos(): List<Todo> = todoDao.listTodos()

    /**
     * Executes deleteTodo.
     */
    fun deleteTodo(todoId: String) = todoDao.deleteTodo(todoId)

    /**
     * Executes clearTodos.
     */
    fun clearTodos() = todoDao.clearTodos()

    /**
     * Executes saveAgent.
     */
    fun saveAgent(
        id: String,
        name: String,
        role: String,
        status: String,
        currentTask: String? = null,
        parentAgentId: String? = null,
        configJson: String,
    ): Boolean = agentDao.saveAgent(id, name, role, status, currentTask, parentAgentId, configJson)

    /**
     * Executes getAgent.
     */
    fun getAgent(id: String): Map<String, Any>? = agentDao.getAgent(id)

    /**
     * Executes listAgents.
     */
    fun listAgents(status: String? = null): List<Map<String, Any>> = agentDao.listAgents(status)

    /**
     * Executes deleteAgent.
     */
    fun deleteAgent(id: String): Boolean = agentDao.deleteAgent(id)

    /**
     * Executes updateAgentStatus.
     */
    fun updateAgentStatus(
        id: String,
        status: String,
        currentTask: String? = null,
    ): Boolean = agentDao.updateAgentStatus(id, status, currentTask)

    /**
     * Executes saveSubAgentConfig.
     */
    fun saveSubAgentConfig(
        id: String,
        name: String,
        description: String,
        model: String,
        systemPrompt: String,
        toolsJson: String,
        maxTurns: Int,
        enabled: Boolean,
    ) = configDao.saveSubAgentConfig(id, name, description, model, systemPrompt, toolsJson, maxTurns, enabled)

    /**
     * Executes getSubAgentConfig.
     */
    fun getSubAgentConfig(id: String): SubAgentToolConfig? = configDao.getSubAgentConfig(id)

    /**
     * Executes listSubAgentConfigs.
     */
    fun listSubAgentConfigs(): List<SubAgentToolConfig> = configDao.listSubAgentConfigs()

    /**
     * Executes close.
     */
    fun close() = Unit
}
