package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.api.AgentModelSelection
import de.heckenmann.visualagent.agent.tools.api.MODEL_SELECTION_TOOL_ID
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionOption
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionPort
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionRequest
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionResult
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** Routes self-model changes through the same persistence services as the settings UI. */
@Component
class ModelSelectionPortAdapter(
    private val catalog: ProviderCatalogService,
    @param:Lazy private val manager: AgentManager,
    @param:Lazy private val permissions: AgentToolConfigService,
    @param:Lazy private val provider: LLMProvider,
) : ModelSelectionPort {
    override fun execute(
        request: ModelSelectionRequest,
        context: Map<String, Any>,
    ): Mono<ModelSelectionResult> =
        if (request.action == "refreshModels") {
            refresh(request, context)
        } else {
            Mono
                .fromCallable {
                    val agent = caller(context)
                    if (request.action == "set") {
                        val providerId =
                            request.providerId ?: agent?.config?.provider?.takeIf(String::isNotBlank) ?: catalog.activeProviderId()
                        return@fromCallable set(agent, providerId, request.modelId)
                    }
                    val current = current(agent)
                    val providerId = request.providerId ?: current.providerId
                    when (request.action) {
                        "get" -> ModelSelectionResult(current)
                        "listProviders" -> page(current, catalog.enabledProviders().map { ModelSelectionOption(it.id, it.name) }, request)
                        "listModels" -> {
                            require(
                                catalog.getProvider(providerId)?.enabled == true,
                            ) { "Provider is missing or disabled. Use listProviders." }
                            page(current, catalog.selectableModels(providerId).map { ModelSelectionOption(it.id, it.name) }, request)
                        }
                        else -> throw IllegalArgumentException(
                            "Unsupported action. Use get, listProviders, listModels, refreshModels, or set.",
                        )
                    }
                }.subscribeOn(Schedulers.boundedElastic())
        }

    private fun refresh(
        request: ModelSelectionRequest,
        context: Map<String, Any>,
    ): Mono<ModelSelectionResult> =
        Mono
            .fromCallable {
                val agent = caller(context)
                require(request.offset in 0..100000 && request.limit in 1..50) { "Invalid discovery page." }
                val id = request.providerId ?: agent?.config?.provider?.takeIf(String::isNotBlank) ?: catalog.activeProviderId()
                requireNotNull(catalog.getProvider(id)?.takeIf { it.enabled }) { "Provider is missing or disabled. Use listProviders." }
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { profile ->
                provider.getModelConfigsReactive(profile).flatMap { discovered ->
                    Mono
                        .fromCallable {
                            val agent = caller(context)
                            require(catalog.getProvider(profile.id)?.enabled == true) { "Provider is missing or disabled." }
                            check(catalog.getProvider(profile.id) == profile) {
                                "Provider configuration changed during refresh. Retry with the current configuration."
                            }
                            catalog.updateDiscoveredModelConfigs(profile.id, discovered)
                            page(current(agent), catalog.selectableModels(profile.id).map { ModelSelectionOption(it.id, it.name) }, request)
                        }.subscribeOn(Schedulers.boundedElastic())
                }
            }

    private fun caller(context: Map<String, Any>): SubAgent? {
        if (!permissions.isToolGloballyEnabled(MODEL_SELECTION_TOOL_ID)) {
            throw SecurityException("Model selection is disabled globally. Enable the tool in settings before retrying.")
        }
        val agentId = context["agentId"] as? String
        val agent =
            if (agentId != null) {
                requireNotNull(manager.getSubAgent(agentId)) { "Calling sub-agent no longer exists." }
            } else {
                require(context["agent"] == "main") { "A trusted agent identity is required." }
                null
            }
        val tools = if (agent == null) permissions.mainAgentTools() else permissions.toolsFor(agent)
        if (ToolId(MODEL_SELECTION_TOOL_ID) !in tools) {
            throw SecurityException("Model selection requires an explicit tool grant for the calling agent.")
        }
        return agent
    }

    private fun current(agent: SubAgent?): AgentModelSelection {
        val resolved = catalog.resolve(agent?.config?.provider, agent?.config?.model)
        return AgentModelSelection(agent?.id ?: "main", resolved.provider.id, resolved.model.id)
    }

    private fun set(
        agent: SubAgent?,
        providerId: String,
        modelId: String?,
    ): ModelSelectionResult {
        require(!modelId.isNullOrBlank()) { "modelId is required. Use listModels to discover selectable models." }
        require(catalog.getProvider(providerId)?.enabled == true) { "Provider is missing or disabled. Use listProviders." }
        require(catalog.selectableModels(providerId).any { it.id == modelId }) {
            "Model is unavailable or filtered. Refresh the provider catalog in settings and use listModels."
        }
        if (agent == null) {
            catalog.setActiveSelection(providerId, modelId)
        } else {
            check(manager.updateAgent(agent.id, config = agent.config.copy(provider = providerId, model = modelId))) {
                "Calling sub-agent no longer exists."
            }
        }
        return ModelSelectionResult(AgentModelSelection(agent?.id ?: "main", providerId, modelId))
    }

    private fun page(
        selection: AgentModelSelection,
        options: List<ModelSelectionOption>,
        request: ModelSelectionRequest,
    ): ModelSelectionResult {
        require(request.offset in 0..100000 && request.limit in 1..50) { "Invalid discovery page." }
        return ModelSelectionResult(selection, options.drop(request.offset).take(request.limit), request.offset, options.size)
    }
}
