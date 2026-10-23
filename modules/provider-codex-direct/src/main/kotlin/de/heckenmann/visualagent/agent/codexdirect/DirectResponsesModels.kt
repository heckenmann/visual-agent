package de.heckenmann.visualagent.agent.codexdirect

import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientAsync
import com.openai.models.responses.inputtokens.InputTokenCountParams
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import de.heckenmann.visualagent.agent.provider.resolveTrustManager
import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.content.Media
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions
import org.springframework.ai.openai.responses.OpenAiResponsesException
import org.springframework.ai.openai.setup.OpenAiSetup
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import javax.net.ssl.SSLContext

/** Owns request-scoped Spring AI SDK resources and applies the server's managed certificate trust. */
@Service
internal class DirectResponsesModels(
    private val trust: ServerTrustManagerProvider? = null,
) {
    /** Opens explicit clients so both transports can be closed after completion or cancellation. */
    fun open(
        bearer: String,
        modelId: String,
    ): DirectResponsesModelLease {
        val manager = trust.resolveTrustManager()
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
        val customizer =
            OpenAiHttpClientBuilderCustomizer { builder ->
                builder.sslSocketFactory(ssl.socketFactory).trustManager(manager)
            }
        val sync =
            OpenAiSetup.setupSyncClient(
                DirectOAuthAttempt.RESOURCE,
                bearer,
                null,
                null,
                null,
                null,
                false,
                false,
                modelId,
                Duration.ofSeconds(120),
                0,
                null,
                emptyMap(),
                ObservationRegistry.NOOP,
                null,
                listOf(customizer),
            )
        try {
            val async =
                OpenAiSetup.setupAsyncClient(
                    DirectOAuthAttempt.RESOURCE,
                    bearer,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    modelId,
                    Duration.ofSeconds(120),
                    0,
                    null,
                    emptyMap(),
                    ObservationRegistry.NOOP,
                    null,
                    listOf(customizer),
                )
            try {
                val model =
                    OpenAiResponsesChatModel
                        .builder()
                        .openAiClient(sync)
                        .openAiClientAsync(async)
                        .options(OpenAiResponsesChatOptions.builder().model(modelId).build())
                        .build()
                return DirectResponsesModelLease(model, sync, async)
            } catch (error: Throwable) {
                async.close()
                throw error
            }
        } catch (error: Throwable) {
            sync.close()
            throw error
        }
    }
}

/** Request-scoped transport ownership; credentials are never included in diagnostic strings. */
internal class DirectResponsesModelLease(
    val model: ChatModel,
    private val sync: OpenAIClient,
    private val async: OpenAIClientAsync,
) : AutoCloseable {
    /** Counts native attachment tokens with the selected model and this lease's credential route. */
    fun countAttachmentTokens(
        media: Media,
        modelId: String,
        token: CancellationToken? = null,
    ): Mono<Int> =
        Mono
            .defer {
                token?.throwIfCancelled()
                val input = directFileInputItem(UserMessage.builder().media(media).build())
                val parameters =
                    InputTokenCountParams
                        .builder()
                        .model(modelId)
                        .inputOfResponseInputItems(listOf(input))
                        .build()
                Mono
                    .fromFuture { async.responses().inputTokens().count(parameters) }
                    .map { result ->
                        val tokens = result.inputTokens()
                        require(tokens in 1..Int.MAX_VALUE.toLong()) { "Invalid native attachment token count" }
                        tokens.toInt()
                    }
            }.onErrorMap { error ->
                if (error is java.util.concurrent.CancellationException) {
                    error
                } else {
                    OpenAiResponsesException("input_token_count_unavailable", "Native input token counting failed.")
                }
            }.let { counting ->
                if (token == null) {
                    counting
                } else {
                    Mono.firstWithSignal(
                        counting,
                        Mono.create<Int> { sink ->
                            val registration =
                                token.onCancelled {
                                    sink.error(
                                        java.util.concurrent.CancellationException("Request cancelled"),
                                    )
                                }
                            sink.onDispose { registration.close() }
                        },
                    )
                }
            }

    override fun close() {
        try {
            async.close()
        } finally {
            sync.close()
        }
    }
}
