package de.heckenmann.visualagent.knowledge

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for persistence infrastructure beans.
 */
@Configuration
internal class KnowledgePersistenceConfig {
    /**
     * Executes connectionProvider.
     */
    @Bean
    fun connectionProvider(connectionManager: KnowledgeConnectionManager): ConnectionProvider =
        ConnectionProvider { connectionManager.getConnection() }
}
