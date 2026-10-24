package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ProviderReplayCodec
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.VisionSupport
import de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.requireDirectCodexEndpoint
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.Media
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** CLI-independent Responses provider with explicitly isolated OAuth and Platform billing routes. */
@Component
internal class CodexDirectProvider(
    private val catalog: ProviderCatalogService,
    private val credentials: DirectProviderCredentials,
    private val models: DirectResponsesModels,
    private val prompts: OpenAiPromptFactory,
    private val execution: ReactiveResponsesToolLoop,
    @Qualifier("codexDirectWebClient") private val webClient: WebClient,
    private val replays: ProviderReplayCodec = ProviderReplayCodec(),
    private val metrics: DirectRequestMetrics = DirectRequestMetrics(),
) : ProfiledProviderAdapter {
    override val adapter = ProviderAdapter.CODEX_DIRECT

    override fun streamReactive(messages: List<Message>): Flux<ChatResponse> = streamReactive(ChatRequestContext(messages))

    override fun streamReactive(request: ChatRequestContext): Flux<ChatResponse> = stream(request, null)

    override fun chatReactive(messages: List<Message>): Mono<ChatResponse> = chatReactive(ChatRequestContext(messages))

    override fun chatReactive(request: ChatRequestContext): Mono<ChatResponse> = complete(streamReactive(request))

    override fun fileReactive(
        file: Media,
        request: ChatRequestContext,
    ): Mono<ChatResponse> =
        Mono
            .defer {
                validateDirectFile(file)
                require(request.messages.any { it.role == "user" }) { "Native file input requires a user prompt" }
                complete(stream(request, file))
            }.onErrorMap(DirectProviderErrors::safe)

    private fun stream(
        request: ChatRequestContext,
        media: Media?,
    ): Flux<ChatResponse> =
        profile(request.providerProfile)
            .flatMapMany { profile ->
                validateEndpoint(profile)
                val selected =
                    request.copy(
                        providerProfile = profile,
                        model = request.model?.ifBlank { null } ?: profile.defaultModel,
                        messages =
                            request.messages.mapIndexed { index, message ->
                                if (message.providerReplay != null &&
                                    message.id == null
                                ) {
                                    message.copy(id = "direct-replay-$index")
                                } else {
                                    message
                                }
                            },
                    )
                metrics.stage(credentials.bearer(profile), DirectRequestStage.CREDENTIAL).flatMapMany { bearer ->
                    Flux.usingWhen(
                        Mono
                            .fromCallable { models.open(bearer, requireNotNull(selected.model)) }
                            .subscribeOn(Schedulers.boundedElastic())
                            .transform { metrics.stage(it, DirectRequestStage.TRANSPORT) }
                            .doOnDiscard(DirectResponsesModelLease::class.java) { it.close() },
                        { lease ->
                            val preparing =
                                media?.let { file ->
                                    metrics.stage(
                                        Mono
                                            .fromCallable {
                                                selected.cancellationToken?.throwIfCancelled()
                                                listOf(DirectPreparedAttachment(file))
                                            }.subscribeOn(Schedulers.boundedElastic()),
                                        DirectRequestStage.ATTACHMENT_PREPARATION,
                                    )
                                } ?: Mono.just(emptyList())
                            preparing.flatMapMany { prepared ->
                                val attachment = prepared.singleOrNull()
                                val counted =
                                    media?.let {
                                        lease
                                            .countAttachmentTokens(
                                                it,
                                                requireNotNull(selected.model),
                                                selected.cancellationToken,
                                                attachment,
                                            ).transform { count -> metrics.stage(count, DirectRequestStage.ATTACHMENT_COUNT) }
                                            .map { tokens ->
                                                selected.copy(additionalInputTokens = Math.addExact(selected.additionalInputTokens, tokens))
                                            }
                                    } ?: Mono.just(selected)
                                counted.flatMapMany { budgeted ->
                                    execution.stream(
                                        DirectResponsesTransport(lease.model, profile.directAuthMode, attachment),
                                        buildPrompt(budgeted, media),
                                        budgeted,
                                    )
                                }
                            }
                        },
                        { lease -> Mono.fromRunnable<Void> { lease.close() }.subscribeOn(Schedulers.boundedElastic()) },
                    )
                }
            }.onErrorMap(DirectProviderErrors::safe)
            .transform(metrics::observe)

    private fun buildPrompt(
        selected: ChatRequestContext,
        media: Media?,
    ): Prompt {
        val profile = requireNotNull(selected.providerProfile)
        val model = requireNotNull(selected.model)
        val prompt = prompts.buildPrompt(selected, model, "codex_direct")
        val options =
            directResponsesOptions(selected, profile.directAuthMode, prompts.callbacks(prompt))
                .mutate()
                .toolContext(selected.metadata + mapOf("provider" to "codex_direct", "model" to model))
                .build()
        val originals = selected.messages.mapNotNull { message -> message.id?.let { it to message } }.toMap()
        val imageMessageIndex = prompt.instructions.indexOfLast { it is UserMessage }
        val messages =
            prompt.instructions.mapIndexed { index, message ->
                val state = originals[message.metadata["visualAgent.sourceMessageId"]]?.providerReplay
                when {
                    message is AssistantMessage && state != null -> replays.restore(state, profile.id, model, message) ?: message
                    message is UserMessage && media != null && index == imageMessageIndex -> message.mutate().media(media).build()
                    else -> message
                }
            }
        return Prompt(messages, options)
    }

    override fun loadModelsReactive(profile: ProviderProfile): Mono<List<ProviderModelConfig>> =
        Mono
            .defer {
                validateEndpoint(profile)
                credentials.bearer(profile).flatMap { token ->
                    webClient
                        .get()
                        .uri("${DirectOAuthAttempt.RESOURCE}/models")
                        .headers { it.setBearerAuth(token) }
                        .retrieve()
                        .bodyToMono(String::class.java)
                        .map { body -> parseDirectModels(body, profile.directAuthMode) }
                }
            }.onErrorMap(DirectProviderErrors::safe)

    override fun getModelsReactive(): Mono<List<String>> = profile(null).flatMap(::getModelsReactive)

    override fun getModelsReactive(profile: ProviderProfile): Mono<List<String>> =
        loadModelsReactive(profile).map {
            it.map(ProviderModelConfig::id)
        }

    override fun getModelDetailsReactive(modelName: String): Mono<ShowResponse> =
        profile(null).flatMap {
            getModelDetailsReactive(it, modelName)
        }

    override fun getModelDetailsReactive(
        profile: ProviderProfile,
        modelName: String,
    ): Mono<ShowResponse> =
        Mono.fromCallable {
            ShowResponse(
                modelName,
                "",
                details = ModelDetails(contextLimit = profile.models.firstOrNull { it.id == modelName }?.contextLimit),
            )
        }

    override fun checkConnectionReactive(): Mono<Boolean> = profile(null).flatMap { checkConnectionReactive(it) }.onErrorReturn(false)

    override fun isConnected(): Boolean =
        catalog.getProvider(catalog.activeProviderId())?.let { profile ->
            profile.adapter == adapter &&
                if (profile.directAuthMode == DirectProviderAuthMode.API_KEY) {
                    profile.apiKey.isNotBlank()
                } else {
                    profile.credentialReference != null
                }
        } == true

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
    ): Mono<ChatResponse> =
        profile(null).flatMap { profile ->
            visionReactive(image, prompt, profile.defaultModel, profile)
        }

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
        modelId: String,
    ): Mono<ChatResponse> = profile(null).flatMap { profile -> visionReactive(image, prompt, modelId, profile) }

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
        modelId: String,
        profile: ProviderProfile,
    ): Mono<ChatResponse> =
        complete(
            stream(
                ChatRequestContext(listOf(Message("user", prompt)), model = modelId, providerProfile = profile),
                VisionSupport.media(image),
            ),
        )

    override fun embeddingsReactive(text: String): Mono<List<Double>> =
        Mono.error(UnsupportedOperationException("Direct Codex does not provide embeddings"))

    private fun profile(supplied: ProviderProfile?): Mono<ProviderProfile> =
        Mono
            .fromCallable {
                supplied ?: requireNotNull(catalog.getProvider(catalog.activeProviderId())) { "Select a direct Codex profile" }
            }.subscribeOn(Schedulers.boundedElastic())

    private fun validateEndpoint(profile: ProviderProfile) {
        profile.requireDirectCodexEndpoint()
    }

    private fun complete(stream: Flux<ChatResponse>): Mono<ChatResponse> =
        Mono.defer {
            val text = StringBuilder()
            var previousRound: Int? = null
            stream
                .map { chunk ->
                    val round = chunk.providerTurn?.metadata?.round
                    if (round != previousRound) text.setLength(0)
                    previousRound = round
                    text.append(chunk.message.content)
                    chunk
                }.last()
                .map { last -> last.copy(message = last.message.copy(content = text.toString()), done = true) }
        }
}
