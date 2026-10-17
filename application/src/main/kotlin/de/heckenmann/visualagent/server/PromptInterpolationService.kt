package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.ModelParameters
import de.heckenmann.visualagent.agent.provider.ModelCapability
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.supportsCapability
import de.heckenmann.visualagent.agent.text.ThinkingMarkup
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.protocol.MAX_INTERPOLATION_CHARACTERS
import de.heckenmann.visualagent.protocol.validateInterpolationDraft
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.CancellationException

/** Generates a non-persistent, tool-free rewrite through the selected provider/model snapshot. */
@Service
class PromptInterpolationService(
    private val provider: LLMProvider,
    private val catalog: ProviderCatalogService,
    private val history: ConversationStore,
    private val config: AppConfigBean,
) {
    private val assembler = PromptInterpolationContext()

    /** Reactively rewrites a draft; cancellation and timeout dispose all upstream work. */
    fun interpolate(
        draft: String,
        token: CancellationToken,
    ): Mono<String> =
        Mono.defer {
            validateInterpolationDraft(draft)
            token.throwIfCancelled()
            Mono
                .fromCallable {
                    val providerId = config.promptInterpolationProviderId.takeIf(String::isNotBlank)
                    val modelId = config.promptInterpolationModelId.takeIf(String::isNotBlank)
                    require(
                        (providerId == null) == (modelId == null),
                    ) { "Choose both an interpolation provider and model, or use the active model" }
                    val resolved = catalog.resolve(providerId, modelId)
                    if (providerId != null) {
                        require(resolved.model.id == modelId && resolved.provider.models.any { it == resolved.model }) {
                            "The interpolation model is unavailable; choose an enabled model in Settings"
                        }
                    }
                    require(resolved.model.supportsCapability(ModelCapability.CHAT)) {
                        "The interpolation model must support CHAT. Choose a chat-capable model in Settings."
                    }
                    resolved
                }.subscribeOn(Schedulers.boundedElastic())
                .flatMap { resolved ->
                    val window = config.promptInterpolationContextMessages.coerceIn(5..10)
                    history
                        .getConversationMessagesReactive("main", window)
                        .collectList()
                        .publishOn(Schedulers.boundedElastic())
                        .flatMap { rows ->
                            val contextLimit = minOf(config.contextLength, resolved.model.contextLimit ?: config.contextLength)
                            val outputLimit = minOf(2_048, contextLimit / 3, resolved.model.outputLimit ?: 2_048).coerceAtLeast(1)
                            val messages = assembler.assemble(draft, rows, contextLimit, outputLimit)
                            provider.chatReactive(
                                ChatRequestContext(
                                    messages = messages,
                                    provider = resolved.provider.id,
                                    providerProfile = resolved.provider,
                                    model = resolved.model.id,
                                    options = resolved.options,
                                    parameters = ModelParameters(maxTokens = outputLimit),
                                    enabledTools = emptySet(),
                                    metadata = mapOf("sessionId" to "main", "agent" to "prompt-interpolation", "thinkingEnabled" to false),
                                    cancellationToken = token,
                                ),
                            )
                        }
                }.map { response ->
                    val text = ThinkingMarkup.remove(response.message.content).trim()
                    require(text.isNotBlank()) { "The model returned an empty prompt; try again or send the original" }
                    require(text.length <= MAX_INTERPOLATION_CHARACTERS) { "The rewritten prompt is too long" }
                    require(
                        response.providerTurn
                            ?.toolCalls
                            .orEmpty()
                            .isEmpty(),
                    ) { "The model returned a tool call instead of a prompt" }
                    text
                }.switchIfEmpty(Mono.error(IllegalStateException("The model returned no prompt")))
                .timeout(Duration.ofSeconds(30))
                // A normal signal makes takeUntilOther cancel the main subscription before reporting cancellation.
                .takeUntilOther(
                    Mono.create<Unit> { sink ->
                        val registration = token.onCancelled { sink.success(Unit) }
                        sink.onDispose { registration.close() }
                    },
                ).switchIfEmpty(Mono.error(CancellationException("Prompt interpolation cancelled")))
                .doFinally { token.cancel() }
        }
}
