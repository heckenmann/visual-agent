package de.heckenmann.visualagent.agent.codexdirect

import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientAsync
import com.openai.models.responses.inputtokens.InputTokenCountParams
import com.openai.models.responses.inputtokens.InputTokenCountResponse
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ContextWindow
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
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectAttachmentCountLifecycleTest {
    @Test
    fun `counting is cold and cancellation disposes the actual SDK future`() {
        val async = mockk<OpenAIClientAsync>()
        val pending = CompletableFuture<InputTokenCountResponse>()
        val started = Sinks.one<Unit>()
        every { async.responses().inputTokens().count(any<InputTokenCountParams>()) } answers {
            started.tryEmitValue(Unit)
            pending
        }
        val lease = DirectResponsesModelLease(mockk(), mockk(), async)
        val token = CancellationToken()
        val result = lease.countAttachmentTokens(file(), "selected", token)
        verify(exactly = 0) { async.responses().inputTokens().count(any<InputTokenCountParams>()) }
        StepVerifier
            .create(Mono.zip(result, started.asMono().doOnNext { token.cancel() }))
            .expectError(CancellationException::class.java)
            .verify()
        verify(exactly = 1) { async.responses().inputTokens().count(any<InputTokenCountParams>()) }
        assertTrue(pending.isCancelled)
    }

    @Test
    fun `a failed count closes both clients and cannot start inference or change credential routes`() {
        DirectProviderAuthMode.entries.forEach { mode ->
            val async = mockk<OpenAIClientAsync>(relaxed = true)
            val sync = mockk<OpenAIClient>(relaxed = true)
            val model = mockk<ChatModel>()
            every { async.responses().inputTokens().count(any<InputTokenCountParams>()) } returns
                CompletableFuture.failedFuture(IllegalStateException("synthetic private error"))
            val credentials = mockk<DirectProviderCredentials>()
            val profile = ProviderProfile("direct", "Direct", ProviderAdapter.CODEX_DIRECT, "https://api.openai.com", directAuthMode = mode)
            every { credentials.bearer(profile) } returns Mono.just("selected-bearer")
            val models = mockk<DirectResponsesModels>()
            every { models.open("selected-bearer", "selected") } returns DirectResponsesModelLease(model, sync, async)
            val registry = mockk<ProviderToolCallbacks>()
            val provider =
                CodexDirectProvider(
                    mockk(),
                    credentials,
                    models,
                    OpenAiPromptFactory(registry),
                    ReactiveResponsesToolLoop(registry),
                    WebClient.create(),
                )
            val request =
                ChatRequestContext(
                    listOf(Message("user", "summarize")),
                    model = "selected",
                    providerProfile = profile,
                    contextWindow = ContextWindow(configuredLimit = 128),
                )

            StepVerifier
                .create(provider.fileReactive(file(), request))
                .expectErrorMatches {
                    assertFalse(it.toString().contains("synthetic private error"))
                    it is ProviderUserFacingException && it.userFacing.summary == "Input token count unavailable"
                }.verify()
            verify(exactly = 0) { model.stream(any<Prompt>()) }
            verify(exactly = 1) { sync.close() }
            verify(exactly = 1) { async.close() }
            verify(exactly = 1) { credentials.bearer(profile) }
            verify(exactly = 1) { models.open("selected-bearer", "selected") }
        }
    }

    private fun file() =
        Media
            .builder()
            .mimeType(MimeType.valueOf("text/plain"))
            .name("notes.txt")
            .data(ByteArrayResource("notes".toByteArray()))
            .build()
}
