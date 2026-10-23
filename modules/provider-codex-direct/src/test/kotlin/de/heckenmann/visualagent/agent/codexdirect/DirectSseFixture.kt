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
import java.util.concurrent.CopyOnWriteArrayList

/** Local-only real SDK transport; synthetic credentials never leave the loopback server. */
internal class DirectSseFixture(
    private val responseFrames: (String) -> Flux<String>,
    private val attachmentTokenCount: Int? = null,
) : AutoCloseable {
    constructor(frames: Flux<String>, attachmentTokenCount: Int? = null) : this({ frames }, attachmentTokenCount)

    val requests = CopyOnWriteArrayList<String>()
    val paths = CopyOnWriteArrayList<String>()
    val disconnected = Sinks.one<Void>()
    private val server =
        HttpServer
            .create()
            .runOn(HttpResources.get(), false)
            .host("127.0.0.1")
            .port(0)
            .handle { request, response ->
                request.withConnection { connection -> connection.onDispose { disconnected.tryEmitEmpty() } }
                request.receive().aggregate().asString().flatMap { body ->
                    requests.add(body)
                    paths.add(request.uri())
                    if (request.uri() == "/v1/responses/input_tokens") {
                        response
                            .header("Content-Type", "application/json")
                            .sendString(
                                Mono.just("""{"object":"response.input_tokens","input_tokens":${requireNotNull(attachmentTokenCount)}}"""),
                            ).then()
                    } else {
                        response.sse().send(responseFrames(body).map { Unpooled.copiedBuffer(it, StandardCharsets.UTF_8) }) { true }.then()
                    }
                }
            }.bindNow()
    private val endpoint = "http://127.0.0.1:${server.port()}/v1"
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
