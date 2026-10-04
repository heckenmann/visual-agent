package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory
import kotlinx.serialization.json.Json
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies native schema options and prompt-only fallback for both Spring AI adapters. */
class StructuredOutputPromptTest {
    private val schema =
        ResponseSchema(
            """{"type":"object","properties":{"verdict":{"type":"string"}},"required":["verdict"],"additionalProperties":false}""",
        )
    private val request = ChatRequestContext(listOf(Message("system", schema.json), Message("user", "review")), responseSchema = schema)

    @Test
    fun `OpenAI native output uses the exact schema and strict response format`() {
        val prompt =
            OpenAiPromptFactory(
                TestToolRegistry(),
            ).buildPrompt(request.copy(options = mapOf("structuredOutput.native" to "true")), "any-model")
        val options = prompt.options as OpenAiChatOptions
        assertEquals(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA, options.responseFormat?.type)
        assertEquals(schema.json, options.outputSchema)
        assertTrue(options.responseFormat?.strict != false)
        assertTrue(options.toolCallbacks.orEmpty().isEmpty())
    }

    @Test
    fun `Ollama native output is a schema object not a JSON string value`() {
        val prompt =
            OllamaPromptFactory(
                TestToolRegistry(),
            ).buildPrompt(request.copy(options = mapOf("structuredOutput.native" to "true")), "any-model")
        val options = prompt.options as OllamaChatOptions
        assertTrue(options.format is Map<*, *>)
        assertEquals(Json.parseToJsonElement(schema.json), Json.parseToJsonElement(requireNotNull(options.outputSchema)))
    }

    @Test
    fun `unknown or disabled native support retains prompt schema without API constraints`() {
        listOf(request, request.copy(options = mapOf("structuredOutput.native" to "false"))).forEach { fallback ->
            val openAi = OpenAiPromptFactory(TestToolRegistry()).buildPrompt(fallback, "any-model")
            val ollama = OllamaPromptFactory(TestToolRegistry()).buildPrompt(fallback, "any-model")
            assertNull((openAi.options as OpenAiChatOptions).responseFormat)
            assertNull((ollama.options as OllamaChatOptions).format)
            assertEquals(schema.json, openAi.instructions.first().text)
            assertEquals(schema.json, ollama.instructions.first().text)
        }
    }
}
