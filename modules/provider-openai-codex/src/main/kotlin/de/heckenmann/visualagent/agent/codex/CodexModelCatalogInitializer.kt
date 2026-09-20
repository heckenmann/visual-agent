package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.logging.Logger

/** Refreshes the active Codex CLI model catalog after application startup. */
@Component
internal class CodexModelCatalogInitializer(
    private val providerCatalog: ProviderCatalogService,
    private val profiledAdapters: List<ProfiledProviderAdapter>,
    private val applicationScope: CoroutineScope,
) {
    /** Loads the active Codex catalog after Spring has initialized all provider services. */
    @EventListener(ApplicationReadyEvent::class)
    fun initializeActiveCodexCatalog() {
        val providerId = providerCatalog.activeProviderId()
        val profile = providerCatalog.getProvider(providerId)?.takeIf { it.adapter == ProviderAdapter.CODEX_CLI } ?: return
        val adapter = profiledAdapters.singleOrNull { it.adapter == ProviderAdapter.CODEX_CLI } ?: return
        applicationScope.launch {
            runCatching {
                adapter.loadModelsReactive(profile).awaitSingle().also { providerCatalog.updateDiscoveredModelConfigs(providerId, it) }
            }.onFailure { error -> logger.warning(ProviderErrorMessages.userFacing(error)) }
        }
    }

    private companion object {
        private val logger = Logger.getLogger(CodexModelCatalogInitializer::class.java.name)
    }
}
