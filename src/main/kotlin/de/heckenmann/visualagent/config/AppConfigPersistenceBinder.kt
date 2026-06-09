package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.knowledge.PreferenceStore
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Represents AppConfigPersistenceBinder.
 */
@Component
class AppConfigPersistenceBinder(
    private val preferenceStore: PreferenceStore,
) {
    /**
     * Executes bind.
     */
    @PostConstruct
    fun bind() {
        AppConfig.instance.bindPreferenceStore(preferenceStore)
    }
}
