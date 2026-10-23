package de.heckenmann.visualagent.agent.codexdirect.benchmark

import com.openai.client.OpenAIClient
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.codexdirect.DirectPreparedAttachment
import de.heckenmann.visualagent.agent.codexdirect.DirectResponsesModelLease
import de.heckenmann.visualagent.agent.codexdirect.DirectResponsesTransport
import de.heckenmann.visualagent.agent.codexdirect.DirectSseFixture
import de.heckenmann.visualagent.agent.codexdirect.DirectSseFrames
import de.heckenmann.visualagent.agent.codexdirect.DirectTransportPool
import de.heckenmann.visualagent.agent.codexdirect.directResponsesOptions
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.micrometer.observation.ObservationRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.Media
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel
import org.springframework.ai.openai.setup.OpenAiSetup
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/** Runs synthetic HTTP/SSE workloads through the official SDK and production transport/loop contracts. */
internal class DirectNetworkBenchmark : AutoCloseable {
    private val fixture = DirectSseFixture(::frames, attachmentTokenCount = 256, captureBodies = false)
    private val pool = DirectTransportPool()
    private val generation = AtomicInteger()
    private val credentials = AtomicInteger()
    private val request = ChatRequestContext(emptyList(), model = "selected")
    private val registry = mockk<ProviderToolCallbacks>()
    private val callback = mockk<ToolCallback>()
    private val loop = ReactiveResponsesToolLoop(registry)

    init {
        every { registry.recordAssistantToolTurn(any(), any()) } returns "synthetic-parent"
        every { registry.bindToolCallRound(any(), any(), any()) } returns AutoCloseable {}
        every { registry.executeToolCallRound(any(), any(), any(), any(), any()) } returns Mono.just(listOf("synthetic result"))
        every { callback.toolDefinition } returns
            ToolDefinition
                .builder()
                .name("workspace_file")
                .description("Synthetic fixture")
                .inputSchema("""{"type":"object"}""")
                .build()
        every { callback.toolMetadata.returnDirect() } returns false
    }

    /** Measures HTTP batches, including their real accepted connection and application-payload counts. */
    fun run() {
        measureNetwork("HTTP cold text", fixture) { sample ->
            DirectTransportPool().let { isolated ->
                try {
                    text(sample, isolated).block()
                } finally {
                    isolated.destroy()
                }
            }
        }
        measureNetwork("HTTP warm text", fixture) { text(it).block() }
        val countedMedia =
            Media
                .builder()
                .mimeType(MimeType.valueOf("application/pdf"))
                .name("synthetic")
                .data(ByteArrayResource(ByteArray(64 * 1024) { 42 }))
                .build()
        val preparedCountInput = DirectPreparedAttachment(countedMedia)
        measureNetwork("HTTP prepared PDF count only", fixture) {
            Mono
                .using(
                    { lease(pool, "synthetic") },
                    { it.countAttachmentTokens(countedMedia, "selected", prepared = preparedCountInput) },
                    DirectResponsesModelLease::close,
                ).block()
        }
        listOf("image/png", "application/pdf").forEach { mime ->
            measureNetwork("HTTP $mime count + inference", fixture) { attachment(mime, it).block() }
        }
        measureNetwork("HTTP two tool rounds", fixture) { text(it, tools = true).block() }
        measureNetwork("HTTP four concurrent requests", fixture) { sample ->
            Flux
                .range(0, 4)
                .flatMap({ text(sample) }, 4)
                .collectList()
                .block()
        }
        measureNetwork("HTTP cancel after first text", fixture) { text(it, cancel = true).block() }
        measureNetwork("HTTP rotated credential", fixture) { sample ->
            text(sample, bearer = "synthetic-${credentials.incrementAndGet()}").block()
        }
        measureNetwork("HTTP replaced trust generation", fixture) { sample ->
            generation.incrementAndGet()
            text(sample).block()
        }
    }

    private fun text(
        sample: NetworkSample,
        owner: DirectTransportPool = pool,
        tools: Boolean = false,
        cancel: Boolean = false,
        bearer: String = "synthetic",
    ): Mono<List<de.heckenmann.visualagent.agent.ChatResponse>> =
        Mono.using(
            { lease(owner, bearer) },
            { lease ->
                val text =
                    if (tools) {
                        "bench_tools"
                    } else if (cancel) {
                        "bench_cancel"
                    } else {
                        "synthetic text"
                    }
                val options = directResponsesOptions(request, DirectProviderAuthMode.API_KEY, if (tools) listOf(callback) else emptyList())
                val prompt = Prompt(listOf(UserMessage(text)), options)
                val stream =
                    loop
                        .stream(
                            DirectResponsesTransport(lease.model, DirectProviderAuthMode.API_KEY),
                            prompt,
                            request,
                        ).doOnNext { sample.text(it.message.content) }
                (if (cancel) stream.filter { it.message.content.isNotEmpty() }.take(1) else stream).collectList()
            },
            DirectResponsesModelLease::close,
        )

    private fun attachment(
        mime: String,
        sample: NetworkSample,
    ): Mono<*> =
        Mono.using(
            { lease(pool, "synthetic") },
            { lease ->
                val media =
                    Media
                        .builder()
                        .mimeType(MimeType.valueOf(mime))
                        .name("synthetic")
                        .data(ByteArrayResource(ByteArray(64 * 1024) { 42 }))
                        .build()
                val prepared = DirectPreparedAttachment(media)
                val prompt =
                    Prompt(
                        listOf(
                            UserMessage
                                .builder()
                                .text("synthetic attachment")
                                .media(media)
                                .build(),
                        ),
                        directResponsesOptions(request, DirectProviderAuthMode.API_KEY, emptyList()),
                    )
                lease.countAttachmentTokens(media, "selected", prepared = prepared).then(
                    DirectResponsesTransport(lease.model, DirectProviderAuthMode.API_KEY, prepared)
                        .stream(prompt)
                        .doOnNext {
                            sample.text(
                                it.result
                                    ?.output
                                    ?.text
                                    .orEmpty(),
                            )
                        }.collectList(),
                )
            },
            DirectResponsesModelLease::close,
        )

    private fun lease(
        owner: DirectTransportPool,
        bearer: String,
    ): DirectResponsesModelLease {
        val shared = owner.acquire("synthetic-policy-${generation.get()}") { LocalClient(fixture.endpoint) }
        val scoped = (shared.resource as LocalClient).root.withOptions { it.apiKey(bearer) }
        val async = scoped.async()
        val model =
            OpenAiResponsesChatModel
                .builder()
                .openAiClient(scoped)
                .openAiClientAsync(async)
                .build()
        return DirectResponsesModelLease(model, scoped, async, shared::close)
    }

    private fun frames(body: String): Flux<String> =
        when {
            body.contains("bench_cancel") -> Flux.just(DirectSseFrames.delta("visible")).concatWith(Flux.never())
            body.contains("bench_tools") -> {
                val outputs =
                    Json.parseToJsonElement(body).jsonObject.getValue("input").jsonArray.count {
                        it.jsonObject["type"]?.jsonPrimitive?.content == "function_call_output"
                    }
                if (outputs < 2) Flux.just(DirectSseFrames.toolCall().replace("_1", "_${outputs + 1}")) else answer()
            }
            else -> answer()
        }

    private fun answer(): Flux<String> = Flux.just(DirectSseFrames.delta("answer"), DirectSseFrames.completed("answer"))

    override fun close() {
        pool.destroy()
        fixture.close()
    }
}

private class LocalClient(
    endpoint: String,
) : AutoCloseable {
    val root: OpenAIClient =
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

    override fun close() = root.close()
}
