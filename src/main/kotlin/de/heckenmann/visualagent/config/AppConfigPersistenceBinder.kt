package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.knowledge.KnowledgeDb
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Connects the AppConfig singleton to the Spring-managed KnowledgeDb bean.
 */
@Component
class AppConfigPersistenceBinder(
    private val knowledgeDb: KnowledgeDb,
) {
    /**
     * Binds the shared database facade once Spring context is initialized.
     */
    @PostConstruct
    fun bind() {
        AppConfig.instance.bindKnowledgeDb(knowledgeDb)
    }
}
