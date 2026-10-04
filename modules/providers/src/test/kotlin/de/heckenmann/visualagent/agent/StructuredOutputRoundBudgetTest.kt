package de.heckenmann.visualagent.agent

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Verifies a second budget pass cannot forget a native response schema. */
class StructuredOutputRoundBudgetTest {
    @Test
    fun `tool loop reserves native schema tokens before invoking the model`() {
        val model = mockk<ChatModel>()
        val schema = ResponseSchema("""{"type":"object","description":"${"oversized ".repeat(300)}"}""")
        val prompt =
            Prompt(
                "review",
                OpenAiChatOptions
                    .builder()
                    .model("test")
                    .outputSchema(schema.json)
                    .build(),
            )
        every { model.call(any<Prompt>()) } returns
            org.springframework.ai.chat.model
                .ChatResponse(listOf(Generation(AssistantMessage("{}"))))
        assertFailsWith<ContextWindowExceededException> {
            ToolCallingLoop(responseSchema = schema)
                .runReactive(model, prompt, null, emptyList(), contextWindow = ContextWindow(configuredLimit = 128))
                .block()
        }
        verify(exactly = 0) { model.call(any<Prompt>()) }
    }
}
