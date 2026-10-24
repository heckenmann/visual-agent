package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import kotlin.test.assertEquals

/** Cache stability must not depend on the registry's incidental iteration order. */
class PromptToolOrderingTest {
    @Test
    fun `both factories preserve identical schemas and guards when registry order changes`() {
        val registry = mockk<ProviderToolCallbacks>()
        val first = callback("alpha")
        val last = callback("omega")
        every { registry.toolRuntimeGuidance() } returns "Use enabled functions only."
        val request = ChatRequestContext(listOf(Message("system", "Stable policy"), Message("user", "Question")))
        val openai = OpenAiPromptFactory(registry)
        val ollama = OllamaPromptFactory(registry)
        listOf<(ChatRequestContext) -> Prompt>(
            { openai.buildPrompt(it, "selected") },
            { ollama.buildPrompt(it, "selected") },
        ).forEach { build ->
            every { registry.functionCallbacks(any(), any()) } returns listOf(last, first)
            val before = build(request)
            every { registry.functionCallbacks(any(), any()) } returns listOf(first, last)
            val after = build(request)
            assertEquals(listOf("alpha", "omega"), names(before))
            assertEquals(names(before), names(after))
            assertEquals(before.instructions, after.instructions)
        }
    }

    private fun names(prompt: Prompt): List<String> =
        (prompt.options as ToolCallingChatOptions).toolCallbacks.orEmpty().map { it.toolDefinition.name() }

    private fun callback(name: String): ToolCallback =
        mockk {
            every { toolDefinition } returns
                ToolDefinition
                    .builder()
                    .name(name)
                    .description("Function $name")
                    .inputSchema("""{"type":"object"}""")
                    .build()
        }
}
