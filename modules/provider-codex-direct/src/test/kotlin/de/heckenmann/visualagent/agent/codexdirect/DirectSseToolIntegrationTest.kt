package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DirectSseToolIntegrationTest {
    @Test
    fun `real OAuth SSE tools preserve reasoning and correlate registry results in the next HTTP request`() {
        val round = AtomicInteger()
        val frames = { _: String ->
            Flux.just(if (round.getAndIncrement() == 0) DirectSseFrames.toolCall() else DirectSseFrames.completed("done"))
        }
        DirectSseFixture(frames).use { fixture ->
            val registry = mockk<ProviderToolCallbacks>()
            every { registry.recordAssistantToolTurn(any(), any()) } returns "parent"
            every { registry.bindToolCallRound(any(), any(), any()) } returns AutoCloseable {}
            every { registry.executeToolCallRound(any(), any(), any(), any(), any()) } returns Mono.just(listOf("managed files"))
            val callback = mockk<ToolCallback>()
            every { callback.toolDefinition } returns
                ToolDefinition
                    .builder()
                    .name("workspace_file")
                    .description("Managed files")
                    .inputSchema("""{"type":"object"}""")
                    .build()
            every { callback.toolMetadata.returnDirect() } returns false
            val request = ChatRequestContext(emptyList(), model = "selected")
            val options = directResponsesOptions(request, DirectProviderAuthMode.CHATGPT_OAUTH, listOf(callback))

            StepVerifier
                .create(
                    ReactiveResponsesToolLoop(registry).stream(
                        DirectResponsesTransport(fixture.model, DirectProviderAuthMode.CHATGPT_OAUTH),
                        Prompt(listOf(UserMessage("list files")), options),
                        request,
                    ),
                ).expectNextCount(1)
                .assertNext { assertEquals("done", it.message.content) }
                .verifyComplete()

            assertEquals(2, fixture.requests.size)
            fixture.requests.forEach { body ->
                val json = Json.parseToJsonElement(body).jsonObject
                assertFalse(json.containsKey("tools"))
                val declaration =
                    json
                        .getValue("input")
                        .jsonArray
                        .first()
                        .jsonObject
                assertEquals("developer", declaration.getValue("role").jsonPrimitive.content)
                val tool =
                    declaration
                        .getValue("tools")
                        .jsonArray
                        .single()
                        .jsonObject
                assertEquals("workspace_file", tool.getValue("name").jsonPrimitive.content)
            }
            val input =
                Json
                    .parseToJsonElement(fixture.requests.last())
                    .jsonObject
                    .getValue("input")
                    .jsonArray
                    .map { it.jsonObject }
            val reasoning = input.single { it["type"]?.jsonPrimitive?.content == "reasoning" }
            assertEquals("opaque-reasoning", reasoning.getValue("encrypted_content").jsonPrimitive.content)
            val call = input.single { it["type"]?.jsonPrimitive?.content == "function_call" }
            val result = input.single { it["type"]?.jsonPrimitive?.content == "function_call_output" }
            assertEquals("call_1", call.getValue("call_id").jsonPrimitive.content)
            assertEquals("call_1", result.getValue("call_id").jsonPrimitive.content)
            assertEquals("managed files", result.getValue("output").jsonPrimitive.content)
            verify(exactly = 1) { registry.executeToolCallRound(any(), any(), any(), setOf("workspace_file"), any()) }
        }
    }
}
