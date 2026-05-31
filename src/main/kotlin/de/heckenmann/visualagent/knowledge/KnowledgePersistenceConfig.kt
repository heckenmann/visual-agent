package de.heckenmann.visualagent.knowledge

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for persistence infrastructure beans.
 */
@Configuration
internal class KnowledgePersistenceConfig {
    /**
     * Exposes a lightweight connection provider for DAO classes.
     *
     * @param connectionManager Shared SQLite connection manager
     * @return Connection provider delegate
     */
    @Bean
    fun connectionProvider(connectionManager: KnowledgeConnectionManager): ConnectionProvider =
        ConnectionProvider { connectionManager.getConnection() }
}
