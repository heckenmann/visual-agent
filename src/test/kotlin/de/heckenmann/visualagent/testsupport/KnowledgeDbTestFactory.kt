package de.heckenmann.visualagent.testsupport

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.knowledge.Memory
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.PersistedSubAgent
import de.heckenmann.visualagent.knowledge.PersistenceStores
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.knowledge.SubAgentConfigStore
import de.heckenmann.visualagent.knowledge.SubAgentStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import de.heckenmann.visualagent.todo.Todo
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Builds isolated Spring Data persistence contexts for SQLite integration tests.
 */
object KnowledgeDbTestFactory {
    /**
     * Creates a persistence fixture for the given SQLite path or JDBC URL.
     *
     * @param dbPath SQLite file path or `jdbc:sqlite:` URL
     * @return Isolated persistence fixture
     */
    fun create(dbPath: String): TestPersistence {
        val context =
            SpringApplicationBuilder(PersistenceTestApplication::class.java)
                .properties(
                    "visual-agent.db.path=$dbPath",
                    "spring.main.web-application-type=none",
                    "spring.main.banner-mode=off",
                    "spring.jpa.hibernate.ddl-auto=validate",
                ).run()
        return TestPersistence(context)
    }
}

/**
 * Test-only aggregate exposing all persistence stores from one isolated context.
 */
class TestPersistence internal constructor(
    private val context: ConfigurableApplicationContext,
) : PersistenceStores,
    AutoCloseable {
    val conversationStore: ConversationStore = context.getBean(ConversationStore::class.java)
    val todoStore: TodoStore = context.getBean(TodoStore::class.java)
    val subAgentStore: SubAgentStore = context.getBean(SubAgentStore::class.java)
    val memoryStore: MemoryStore = context.getBean(MemoryStore::class.java)
    val preferenceStore: PreferenceStore = context.getBean(PreferenceStore::class.java)
    val subAgentConfigStore: SubAgentConfigStore = context.getBean(SubAgentConfigStore::class.java)
    val workspaceFileStore: WorkspaceFileStore = context.getBean(WorkspaceFileStore::class.java)

    fun createAgentManager(
        provider: LLMProvider,
        toolEventBus: ToolEventBus = ToolEventBus(),
    ): AgentManager {
        val configService = AgentToolConfigService(subAgentConfigStore)
        return AgentManager(
            conversationStore,
            todoStore,
            subAgentStore,
            memoryStore,
            provider,
            configService,
            toolEventBus,
        )
    }

    override fun saveConversationMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
    ): String = conversationStore.saveConversationMessage(sessionId, role, content, metadata)

    override fun getConversationMessages(
        sessionId: String,
        limit: Int,
    ) = conversationStore.getConversationMessages(sessionId, limit)

    override fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ) = conversationStore.getConversationMessagesPage(sessionId, limit, offset)

    override fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int,
    ) = conversationStore.searchConversationMessages(sessionId, query, limit)

    override fun deleteConversationMessages(sessionId: String): Int = conversationStore.deleteConversationMessages(sessionId)

    override fun saveWorkspaceFile(record: WorkspaceFileRecord) = workspaceFileStore.saveWorkspaceFile(record)

    override fun listWorkspaceFiles(): List<WorkspaceFileRecord> = workspaceFileStore.listWorkspaceFiles()

    override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = workspaceFileStore.getWorkspaceFile(id)

    override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? =
        workspaceFileStore.getWorkspaceFileByPath(relativePath)

    override fun deleteWorkspaceFile(id: String): Boolean = workspaceFileStore.deleteWorkspaceFile(id)

    override fun saveTodo(todo: Todo) = todoStore.saveTodo(todo)

    override fun listTodos(): List<Todo> = todoStore.listTodos()

    override fun deleteTodo(todoId: String) = todoStore.deleteTodo(todoId)

    override fun clearTodos() = todoStore.clearTodos()

    override fun saveMemory(
        content: String,
        tags: List<String>,
    ): String = memoryStore.saveMemory(content, tags)

    override fun saveStructuredKnowledge(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): String = memoryStore.saveStructuredKnowledge(subject, summary, nextSteps)

    override fun searchMemories(
        query: String,
        limit: Int,
    ): List<Memory> = memoryStore.searchMemories(query, limit)

    override fun getPreference(key: String): String? = preferenceStore.getPreference(key)

    override fun setPreference(
        key: String,
        value: String,
    ) = preferenceStore.setPreference(key, value)

    override fun saveAgent(agent: PersistedSubAgent): Boolean = subAgentStore.saveAgent(agent)

    override fun getAgent(id: String): PersistedSubAgent? = subAgentStore.getAgent(id)

    override fun listAgents(status: String?): List<PersistedSubAgent> = subAgentStore.listAgents(status)

    override fun deleteAgent(id: String): Boolean = subAgentStore.deleteAgent(id)

    override fun updateAgentStatus(
        id: String,
        status: String,
        currentTask: String?,
    ): Boolean = subAgentStore.updateAgentStatus(id, status, currentTask)

    override fun saveSubAgentConfig(config: SubAgentToolConfig) = subAgentConfigStore.saveSubAgentConfig(config)

    override fun getSubAgentConfig(id: String): SubAgentToolConfig? = subAgentConfigStore.getSubAgentConfig(id)

    override fun listSubAgentConfigs(): List<SubAgentToolConfig> = subAgentConfigStore.listSubAgentConfigs()

    override fun close() = context.close()
}

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan("de.heckenmann.visualagent.knowledge")
@EntityScan("de.heckenmann.visualagent.knowledge")
@EnableJpaRepositories("de.heckenmann.visualagent.knowledge")
internal class PersistenceTestApplication
