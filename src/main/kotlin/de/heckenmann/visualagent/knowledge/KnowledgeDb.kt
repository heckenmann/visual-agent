package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.SubAgentToolConfig
import de.heckenmann.visualagent.todo.Todo
import org.springframework.stereotype.Repository

/**
 * SQLite facade for Visual Agent persistence.
 *
 * This class delegates table-specific operations to focused DAO beans.
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

    fun saveMemory(
        content: String,
        tags: List<String> = emptyList(),
    ): String = memoryDao.saveMemory(content, tags)

    fun saveStructuredKnowledge(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): String = memoryDao.saveStructuredKnowledge(subject, summary, nextSteps)

    fun searchMemories(
        query: String,
        limit: Int = 10,
    ): List<Memory> = memoryDao.searchMemories(query, limit)

    fun getPreference(key: String): String? = preferenceDao.getPreference(key)

    fun setPreference(
        key: String,
        value: String,
    ) = preferenceDao.setPreference(key, value)

    fun saveConversationMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
    ): String = conversationDao.saveConversationMessage(sessionId, role, content, metadata)

    fun getConversationMessages(
        sessionId: String,
        limit: Int = 500,
    ): List<Map<String, String>> = conversationDao.getConversationMessages(sessionId, limit)

    fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<Map<String, String>> = conversationDao.getConversationMessagesPage(sessionId, limit, offset)

    fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int = 20,
    ): List<Map<String, String>> = conversationDao.searchConversationMessages(sessionId, query, limit)

    fun deleteConversationMessages(sessionId: String): Int = conversationDao.deleteConversationMessages(sessionId)

    fun saveTodo(todo: Todo) = todoDao.saveTodo(todo)

    fun listTodos(): List<Todo> = todoDao.listTodos()

    fun deleteTodo(todoId: String) = todoDao.deleteTodo(todoId)

    fun clearTodos() = todoDao.clearTodos()

    fun saveAgent(
        id: String,
        name: String,
        role: String,
        status: String,
        currentTask: String? = null,
        parentAgentId: String? = null,
        configJson: String,
    ): Boolean = agentDao.saveAgent(id, name, role, status, currentTask, parentAgentId, configJson)

    fun getAgent(id: String): Map<String, Any>? = agentDao.getAgent(id)

    fun listAgents(status: String? = null): List<Map<String, Any>> = agentDao.listAgents(status)

    fun deleteAgent(id: String): Boolean = agentDao.deleteAgent(id)

    fun updateAgentStatus(
        id: String,
        status: String,
        currentTask: String? = null,
    ): Boolean = agentDao.updateAgentStatus(id, status, currentTask)

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

    fun getSubAgentConfig(id: String): SubAgentToolConfig? = configDao.getSubAgentConfig(id)

    fun listSubAgentConfigs(): List<SubAgentToolConfig> = configDao.listSubAgentConfigs()

    fun close() = Unit
}
