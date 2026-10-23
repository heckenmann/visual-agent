package de.heckenmann.visualagent.agent.codexdirect

import io.micrometer.observation.ObservationRegistry
import io.netty.buffer.Unpooled
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions
import org.springframework.ai.openai.setup.OpenAiSetup
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.netty.http.HttpResources
import reactor.netty.http.server.HttpServer
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** Local-only real SDK transport; synthetic credentials never leave the loopback server. */
internal class DirectSseFixture(
    private val responseFrames: (String) -> Flux<String>,
    private val attachmentTokenCount: Int? = null,
    private val captureBodies: Boolean = true,
) : AutoCloseable {
    constructor(frames: Flux<String>, attachmentTokenCount: Int? = null) : this({ frames }, attachmentTokenCount)

    val requests = CopyOnWriteArrayList<String>()
    val paths = CopyOnWriteArrayList<String>()
    val authorizationHeaders = CopyOnWriteArrayList<String>()
    val disconnected = Sinks.one<Void>()
    val connections = AtomicLong()
    private val connectionIds = ConcurrentHashMap.newKeySet<String>()
    val requestCount = AtomicLong()
    val receivedBytes = AtomicLong()
    val sentBytes = AtomicLong()
    private val server =
        HttpServer
            .create()
            .runOn(HttpResources.get(), false)
            .host("127.0.0.1")
            .port(0)
            .doOnConnection { if (connectionIds.add(it.channel().id().asLongText())) connections.incrementAndGet() }
            .handle { request, response ->
                authorizationHeaders.add(request.requestHeaders().get("Authorization").orEmpty())
                request.withConnection { connection -> connection.onDispose { disconnected.tryEmitEmpty() } }
                request.receive().aggregate().asString().flatMap { body ->
                    requestCount.incrementAndGet()
                    receivedBytes.addAndGet(body.toByteArray(StandardCharsets.UTF_8).size.toLong())
                    if (captureBodies) requests.add(body)
                    paths.add(request.uri())
                    if (request.uri() == "/v1/responses/input_tokens") {
                        val payload = """{"object":"response.input_tokens","input_tokens":${requireNotNull(attachmentTokenCount)}}"""
                        sentBytes.addAndGet(payload.toByteArray(StandardCharsets.UTF_8).size.toLong())
                        response
                            .header("Content-Type", "application/json")
                            .sendString(
                                Mono.just(payload),
                            ).then()
                    } else {
                        response
                            .sse()
                            .send(
                                responseFrames(body).map {
                                    val bytes = it.toByteArray(StandardCharsets.UTF_8)
                                    sentBytes.addAndGet(bytes.size.toLong())
                                    Unpooled.wrappedBuffer(bytes)
                                },
                            ) { true }
                            .then()
                    }
                }
            }.bindNow()
    val endpoint = "http://127.0.0.1:${server.port()}/v1"
    private val sync =
        OpenAiSetup.setupSyncClient(
            endpoint,
            "synthetic",
            null,
            null,
            null,
            null,
            false,
            false,
            "selected",
            Duration.ofSeconds(30),
            0,
            null,
            emptyMap(),
            ObservationRegistry.NOOP,
            null,
            emptyList(),
        )
    private val async =
        OpenAiSetup.setupAsyncClient(
            endpoint,
            "synthetic",
            null,
            null,
            null,
            null,
            false,
            false,
            "selected",
            Duration.ofSeconds(30),
            0,
            null,
            emptyMap(),
            ObservationRegistry.NOOP,
            null,
            emptyList(),
        )
    val model =
        OpenAiResponsesChatModel
            .builder()
            .openAiClient(sync)
            .openAiClientAsync(async)
            .options(OpenAiResponsesChatOptions.builder().model("selected").build())
            .build()

    /** Supplies the same synthetic SDK clients to the production provider lifecycle. */
    fun lease() = DirectResponsesModelLease(model, sync, async)

    /** Exercises official SDK credential views sharing the root client's underlying transport. */
    fun scopedModel(bearer: String): OpenAiResponsesChatModel {
        val scoped = sync.withOptions { it.apiKey(bearer) }
        return OpenAiResponsesChatModel
            .builder()
            .openAiClient(scoped)
            .openAiClientAsync(scoped.async())
            .options(OpenAiResponsesChatOptions.builder().model("selected").build())
            .build()
    }

    override fun close() {
        try {
            async.close()
        } finally {
            try {
                sync.close()
            } finally {
                server.disposeNow()
            }
        }
    }
}
