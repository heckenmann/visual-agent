package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.MessageAggregator
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions
import org.springframework.ai.openai.responses.OpenAiResponsesException
import org.springframework.ai.openai.responses.OpenAiResponsesMetadata
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

/** Streams every Responses model turn, including tool continuations, through the existing registry. */
@Component
class ReactiveResponsesToolLoop(
    private val registry: ProviderToolCallbacks,
    private val budgeter: RequestContextBudgeter = RequestContextBudgeter(),
    private val replays: ProviderReplayCodec = ProviderReplayCodec(),
) {
    /** Executes a cold, cancellable stream without falling back to synchronous model calls. */
    fun stream(
        model: ChatModel,
        prompt: Prompt,
        request: ChatRequestContext,
    ): Flux<ChatResponse> =
        round(model, prompt, request, 0).let { stream ->
            request.cancellationToken?.let { token ->
                stream.takeUntilOther(
                    Mono.create<Any> { sink ->
                        val registration = token.onCancelled { sink.error(CancellationException("Request cancelled")) }
                        sink.onDispose { registration.close() }
                    },
                )
            } ?: stream
        }

    private fun round(
        model: ChatModel,
        prompt: Prompt,
        request: ChatRequestContext,
        round: Int,
    ): Flux<ChatResponse> =
        Flux.defer {
            request.cancellationToken?.throwIfCancelled()
            check(round < MAX_ROUNDS) { "Responses tool round limit reached" }
            val callbacks = (prompt.options as? ToolCallingChatOptions)?.toolCallbacks.orEmpty()
            val bounded =
                budgeter.fitPrompt(request, prompt, callbacks) { options, limit ->
                    val responses = options as? OpenAiResponsesChatOptions
                    if (responses == null ||
                        request.providerProfile?.directAuthMode == DirectProviderAuthMode.CHATGPT_OAUTH
                    ) {
                        options
                    } else {
                        responses.mutate().maxOutputTokens(limit).build()
                    }
                }
            val aggregated = AtomicReference<SpringChatResponse>()
            val terminalStatus = AtomicReference<String>()
            MessageAggregator()
                .aggregate(
                    model.stream(bounded).withResponsePartLimit(replays).doOnNext { chunk ->
                        chunk.metadata.get<String>(OpenAiResponsesMetadata.STATUS)?.let(terminalStatus::set)
                    },
                    aggregated::set,
                ).index()
                .map { indexed ->
                    indexed.t2.toVisualAgentResponse(
                        requestId = request.metadata["requestId"]?.toString(),
                        round = round,
                        sequence = indexed.t1.toInt(),
                        normalizeContent = false,
                    )
                }.concatWith(
                    Flux.defer {
                        val response = aggregated.get()
                        val status = terminalStatus.get()
                        if (status != "completed") {
                            throw OpenAiResponsesException(
                                if (status == "incomplete") "response_incomplete" else "missing_response_completed",
                                "The Responses stream did not complete successfully.",
                            )
                        }
                        if (response?.hasToolCalls() != true) {
                            val profile = request.providerProfile ?: return@defer Flux.empty()
                            val output = response?.result?.output ?: return@defer Flux.empty()
                            val state = replays.encode(profile.id, requireNotNull(request.model), output)
                            val turn =
                                ProviderTurnResponseMapper
                                    .fromSpring(response, round = round)
                                    .copy(content = "", reasoning = null, providerReplay = state)
                            return@defer Flux.just(ProviderTurnResponseMapper.toChatResponse(turn, normalizeContent = false))
                        }
                        Mono
                            .fromCallable {
                                registry.recordAssistantToolTurn(
                                    ProviderTurnResponseMapper.fromSpring(response, round = round),
                                    request.metadata,
                                ) ?: ""
                            }.flatMap { parent ->
                                val turn = ProviderTurnResponseMapper.fromSpring(response, round = round)
                                Mono.using(
                                    { registry.bindToolCallRound(turn.toolCalls, round, parent.ifEmpty { null }) },
                                    {
                                        executeCorrelatedToolsReactive(
                                            ToolCallingManager.builder().build(),
                                            bounded,
                                            response,
                                            registry,
                                            turn,
                                            round,
                                            parent.ifEmpty { null },
                                            callbacks,
                                            request.metadata +
                                                (request.cancellationToken?.let { mapOf("cancellationToken" to it) } ?: emptyMap()),
                                        )
                                    },
                                    AutoCloseable::close,
                                )
                            }.subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany { result ->
                                if (result.returnDirect()) {
                                    Flux.just(buildDirectResponse(response, result))
                                } else {
                                    round(model, appendToolConversationHistory(bounded, result), request, round + 1)
                                }
                            }
                    },
                )
        }

    private companion object {
        const val MAX_ROUNDS = 5
    }
}
