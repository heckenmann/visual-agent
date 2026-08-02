package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.ConfiguredLLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.logging.Logger

/** Refreshes the active Codex model catalog independently from Compose panel visibility. */
@Component
internal class CodexModelCatalogInitializer(
    private val providerCatalog: ProviderCatalogService,
    private val llmProvider: ConfiguredLLMProvider,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Starts model discovery after Spring has completed application startup. */
    @EventListener(ApplicationReadyEvent::class)
    fun initializeActiveCodexCatalog() {
        val providerId = providerCatalog.activeProviderId()
        val profile = providerCatalog.getProvider(providerId) ?: return
        if (profile.adapter != ProviderAdapter.CODEX_CLI) return
        applicationScope.launch(ioDispatcher) {
            runCatching { llmProvider.getModels(providerId) }
                .onFailure { error -> logger.warning(ProviderErrorMessages.userFacing(error)) }
        }
    }

    private companion object {
        private val logger = Logger.getLogger(CodexModelCatalogInitializer::class.java.name)
    }
}
