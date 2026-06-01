package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.knowledge.KnowledgeDb
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Represents AppConfigPersistenceBinder.
 */
@Component
class AppConfigPersistenceBinder(
    private val knowledgeDb: KnowledgeDb,
) {
    /**
     * Executes bind.
     */
    @PostConstruct
    fun bind() {
        AppConfig.instance.bindKnowledgeDb(knowledgeDb)
    }
}
