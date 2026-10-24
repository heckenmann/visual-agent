package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ContextWindow
import de.heckenmann.visualagent.agent.ContextWindowExceededException
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.ai.content.Media
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectAttachmentBudgetTest {
    @Test
    fun `native tool continuations retain the attachment reservation without recounting or losing it`() {
        DirectProviderAuthMode.entries.forEach { mode ->
            val rounds = AtomicInteger()
            DirectSseFixture({
                Flux.just(
                    if (rounds.getAndIncrement() ==
                        0
                    ) {
                        DirectSseFrames.toolCall()
                    } else {
                        DirectSseFrames.completed("done")
                    },
                )
            }, 1000)
                .use { fixture ->
                    val registry = mockk<ProviderToolCallbacks>()
                    val callback = mockk<ToolCallback>()
                    every { callback.toolDefinition } returns
                        ToolDefinition
                            .builder()
                            .name("workspace_file")
                            .description("Managed files")
                            .inputSchema("""{"type":"object"}""")
                            .build()
                    every { callback.toolMetadata.returnDirect() } returns false
                    every { registry.toolRuntimeGuidance() } returns "Use native tools."
                    every { registry.recordAssistantToolTurn(any(), any()) } returns "parent"
                    every { registry.bindToolCallRound(any(), any(), any()) } returns AutoCloseable {}
                    every { registry.executeToolCallRound(any(), any(), any(), any(), any()) } returns Mono.just(listOf("managed files"))
                    val request = request(mode).copy(contextWindow = ContextWindow(configuredLimit = 4096))
                    StepVerifier
                        .create(
                            provider(fixture, registry, listOf(callback)).fileReactive(media("text/plain", "notes.txt"), request),
                        ).assertNext { assertEquals("done", it.message.content) }
                        .verifyComplete()
                    assertEquals(listOf("/v1/responses/input_tokens", "/v1/responses", "/v1/responses"), fixture.paths)
                    fixture.requests.drop(1).forEach { body ->
                        val json = Json.parseToJsonElement(body).jsonObject
                        if (mode == DirectProviderAuthMode.API_KEY) {
                            assertEquals("774", json.getValue("max_output_tokens").jsonPrimitive.content)
                        } else {
                            assertTrue("max_output_tokens" !in json)
                        }
                    }
                }
        }
    }

    @Test
    fun `oversized native input is rejected before inference for both credential routes and all supported media kinds`() {
        DirectProviderAuthMode.entries.forEach { mode ->
            listOf("text/plain" to "notes.txt", "application/pdf" to "report.pdf", "image/png" to "photo.png").forEach { (mime, name) ->
                DirectSseFixture(Flux.just(DirectSseFrames.completed("unexpected")), 1000).use { fixture ->
                    val provider = provider(fixture)
                    StepVerifier
                        .create(provider.fileReactive(media(mime, name), request(mode)))
                        .expectError(ContextWindowExceededException::class.java)
                        .verify()
                    assertEquals(listOf("/v1/responses/input_tokens"), fixture.paths)
                    val body = Json.parseToJsonElement(fixture.requests.single()).jsonObject
                    assertEquals("selected", body.getValue("model").jsonPrimitive.content)
                    val content =
                        body
                            .getValue("input")
                            .jsonArray
                            .single()
                            .jsonObject
                            .getValue("content")
                            .jsonArray
                            .single()
                            .jsonObject
                    assertEquals(
                        if (mime.startsWith("image/")) "input_image" else "input_file",
                        content.getValue("type").jsonPrimitive.content,
                    )
                }
            }
        }
    }

    @Test
    fun `provider token count rather than byte count determines whether the file fits`() {
        DirectProviderAuthMode.entries.forEach { mode ->
            DirectSseFixture(Flux.just(DirectSseFrames.completed("summary")), 12).use { fixture ->
                val file = media("text/plain", "notes.txt", "large file content ".repeat(15000).toByteArray())
                StepVerifier
                    .create(provider(fixture).fileReactive(file, request(mode)))
                    .assertNext { assertEquals("summary", it.message.content) }
                    .verifyComplete()
                assertEquals(listOf("/v1/responses/input_tokens", "/v1/responses"), fixture.paths)
                val countInput = Json.parseToJsonElement(fixture.requests.first()).jsonObject.getValue("input")
                val inference = Json.parseToJsonElement(fixture.requests.last()).jsonObject
                val inferenceFile =
                    inference
                        .getValue("input")
                        .jsonArray
                        .last()
                        .jsonObject
                        .getValue("content")
                        .jsonArray
                        .last()
                val countedFile =
                    countInput.jsonArray
                        .single()
                        .jsonObject
                        .getValue("content")
                        .jsonArray
                        .single()
                assertEquals(countedFile, inferenceFile)
                if (mode == DirectProviderAuthMode.API_KEY) {
                    assertEquals("29", inference.getValue("max_output_tokens").jsonPrimitive.content)
                } else {
                    assertTrue("max_output_tokens" !in inference)
                }
            }
        }
    }

    @Test
    fun `an invalid native count fails closed and never starts inference`() {
        DirectSseFixture(Flux.empty(), -1).use { fixture ->
            StepVerifier
                .create(provider(fixture).fileReactive(media("text/plain", "notes.txt"), request(DirectProviderAuthMode.API_KEY)))
                .expectErrorMatches { it is ProviderUserFacingException && it.userFacing.summary == "Input token count unavailable" }
                .verify()
            assertEquals(listOf("/v1/responses/input_tokens"), fixture.paths)
        }
    }

    @Test
    fun `ordinary text chat does not perform a native token count request`() {
        DirectSseFixture(Flux.just(DirectSseFrames.completed("answer"))).use { fixture ->
            StepVerifier
                .create(provider(fixture).chatReactive(request(DirectProviderAuthMode.API_KEY)))
                .assertNext { assertEquals("answer", it.message.content) }
                .verifyComplete()
            assertEquals(listOf("/v1/responses"), fixture.paths)
        }
    }

    private fun provider(
        fixture: DirectSseFixture,
        registry: ProviderToolCallbacks = mockk(),
        callbacks: List<ToolCallback> = emptyList(),
    ): CodexDirectProvider {
        val credentials = mockk<DirectProviderCredentials>()
        every { credentials.bearer(any()) } returns Mono.just("synthetic")
        val models = mockk<DirectResponsesModels>()
        every { models.open("synthetic", "selected") } returns fixture.lease()
        every { registry.functionCallbacks(any(), any()) } returns callbacks
        return CodexDirectProvider(
            mockk(),
            credentials,
            models,
            OpenAiPromptFactory(registry),
            ReactiveResponsesToolLoop(registry),
            WebClient.create(),
        )
    }

    private fun request(mode: DirectProviderAuthMode) =
        ChatRequestContext(
            listOf(Message("user", "summarize")),
            model = "selected",
            providerProfile =
                ProviderProfile(
                    "direct",
                    "Direct",
                    ProviderAdapter.CODEX_DIRECT,
                    "https://api.openai.com",
                    directAuthMode = mode,
                ),
            contextWindow = ContextWindow(configuredLimit = 128),
        )

    private fun media(
        mime: String,
        name: String,
        bytes: ByteArray = byteArrayOf(1),
    ) = Media
        .builder()
        .mimeType(MimeType.valueOf(mime))
        .name(name)
        .data(ByteArrayResource(bytes))
        .build()
}
