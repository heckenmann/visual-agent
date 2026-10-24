package de.heckenmann.visualagent.agent.codexdirect

import com.openai.client.OpenAIClient
import com.openai.core.RequestOptions
import com.openai.core.jsonMapper
import com.openai.models.responses.ResponseCreateParams
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DirectResponsesTransportTest {
    @Test
    fun `OAuth emits developer additional tools through the real framework request builder`() {
        val callback = callback()
        val prompt = prompt(listOf(callback), DirectProviderAuthMode.CHATGPT_OAUTH)
        val captured = slot<ResponseCreateParams>()
        val client = mockk<OpenAIClient>(relaxed = true)
        every { client.responses().create(capture(captured), any<RequestOptions>()) } throws IllegalStateException("captured")
        val native =
            OpenAiResponsesChatModel
                .builder()
                .openAiClient(client)
                .openAiClientAsync(mockk())
                .build()

        assertFailsWith<IllegalStateException> {
            DirectResponsesTransport(native, DirectProviderAuthMode.CHATGPT_OAUTH).call(prompt)
        }

        val body = Json.parseToJsonElement(jsonMapper().writeValueAsString(captured.captured._body())).jsonObject
        assertFalse(body.containsKey("tools"))
        val input = body.getValue("input").jsonArray
        val declaration = input.first().jsonObject
        assertEquals("additional_tools", declaration.getValue("type").jsonPrimitive.content)
        assertEquals("developer", declaration.getValue("role").jsonPrimitive.content)
        val function =
            declaration
                .getValue("tools")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("workspace_file", function.getValue("name").jsonPrimitive.content)
        assertEquals("function", function.getValue("type").jsonPrimitive.content)
        assertEquals(
            "object",
            function
                .getValue("parameters")
                .jsonObject
                .getValue("type")
                .jsonPrimitive.content,
        )
        assertEquals(
            "question",
            input
                .last()
                .jsonObject
                .getValue("content")
                .jsonPrimitive.content,
        )
        assertEquals(listOf(callback), (prompt.options as org.springframework.ai.openai.responses.OpenAiResponsesChatOptions).toolCallbacks)
    }

    @Test
    fun `API key declarations and requests without tools are not rewritten`() {
        val delegate = mockk<ChatModel>()
        val apiPrompt = prompt(listOf(callback()), DirectProviderAuthMode.API_KEY)
        assertSame(apiPrompt, DirectResponsesTransport(delegate, DirectProviderAuthMode.API_KEY).transportPrompt(apiPrompt))
        val noTools = prompt(emptyList(), DirectProviderAuthMode.CHATGPT_OAUTH)
        assertSame(noTools, DirectResponsesTransport(delegate, DirectProviderAuthMode.CHATGPT_OAUTH).transportPrompt(noTools))
    }

    @Test
    fun `transport adaptation never accumulates duplicate declarations on the execution prompt`() {
        val original = prompt(listOf(callback()), DirectProviderAuthMode.CHATGPT_OAUTH)
        val transport = DirectResponsesTransport(mockk(), DirectProviderAuthMode.CHATGPT_OAUTH)
        val first = transport.transportPrompt(original)
        val second = transport.transportPrompt(original)

        assertEquals(1, original.instructions.size)
        assertEquals(2, first.instructions.size)
        assertEquals(first.instructions, second.instructions)
        assertTrue((first.options as org.springframework.ai.openai.responses.OpenAiResponsesChatOptions).toolCallbacks.orEmpty().isEmpty())
    }

    private fun prompt(
        callbacks: List<ToolCallback>,
        mode: DirectProviderAuthMode,
    ) = Prompt(
        listOf(UserMessage("question")),
        directResponsesOptions(ChatRequestContext(emptyList(), model = "selected"), mode, callbacks),
    )

    private fun callback(): ToolCallback =
        mockk<ToolCallback>().also {
            every { it.toolDefinition } returns
                ToolDefinition
                    .builder()
                    .name("workspace_file")
                    .description("Managed files")
                    .inputSchema("""{"type":"object"}""")
                    .build()
        }
}
