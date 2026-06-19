package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.knowledge.PreferenceStore
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Connects the singleton application configuration to the persisted preference store.
 */
@Component
class AppConfigPersistenceBinder(
    private val preferenceStore: PreferenceStore,
) {
    /**
     * Installs the preference store after Spring has constructed persistence beans.
     */
    @PostConstruct
    fun bind() {
        AppConfig.instance.bindPreferenceStore(preferenceStore)
    }
}
