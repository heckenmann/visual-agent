package de.heckenmann.visualagent.testsupport

import de.heckenmann.visualagent.knowledge.ConnectionProvider
import de.heckenmann.visualagent.knowledge.ConversationDao
import de.heckenmann.visualagent.knowledge.KnowledgeConnectionManager
import de.heckenmann.visualagent.knowledge.KnowledgeDb
import de.heckenmann.visualagent.knowledge.KnowledgeSchema
import de.heckenmann.visualagent.knowledge.MemoryDao
import de.heckenmann.visualagent.knowledge.PreferenceDao
import de.heckenmann.visualagent.knowledge.SubAgentConfigDao
import de.heckenmann.visualagent.knowledge.SubAgentDao
import de.heckenmann.visualagent.knowledge.TodoDao

/**
 * Builds a fully wired [KnowledgeDb] instance for tests without Spring context bootstrap.
 */
object KnowledgeDbTestFactory {
    /**
     * Creates a [KnowledgeDb] instance for the given SQLite path/url.
     *
     * @param dbPath SQLite file path or `jdbc:sqlite:` URL
     * @return Fully wired KnowledgeDb test instance
     */
    fun create(dbPath: String): KnowledgeDb {
        val manager = KnowledgeConnectionManager(dbPath)
        val provider = ConnectionProvider { manager.getConnection() }
        return KnowledgeDb(
            knowledgeSchema = KnowledgeSchema(provider),
            memoryDao = MemoryDao(provider),
            preferenceDao = PreferenceDao(provider),
            conversationDao = ConversationDao(provider),
            todoDao = TodoDao(provider),
            agentDao = SubAgentDao(provider),
            configDao = SubAgentConfigDao(provider),
        )
    }
}
