package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.CancellationToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import kotlin.test.assertEquals

class CodexCliChatModelTest {
    @Test
    fun `call maps app server completion to spring ai response`() {
        val bridge = FakeBridge(completion = CodexAppServerChatResult("gpt-5.6-terra", "Completed response"))
        val model = CodexCliChatModel(bridge)
        val prompt = Prompt("Hello")

        val response = model.call(prompt)

        assertEquals(prompt, bridge.completedPrompt)
        assertEquals("gpt-5.6-terra", response.metadata.model)
        assertEquals("Completed response", requireNotNull(response.result).output.text)
    }

    @Test
    fun `stream preserves ordered app server deltas`() {
        val bridge =
            FakeBridge(
                chunks =
                    listOf(
                        CodexAppServerChatChunk("gpt-5.6-terra", "Hello ", terminal = false),
                        CodexAppServerChatChunk("gpt-5.6-terra", "world", terminal = true),
                    ),
            )
        val model = CodexCliChatModel(bridge)
        val prompt = Prompt("Hello")

        val responses = model.stream(prompt).collectList().block()!!

        assertEquals(prompt, bridge.streamedPrompt)
        assertEquals(listOf("Hello ", "world"), responses.map { requireNotNull(it.result).output.text })
        assertEquals(listOf("gpt-5.6-terra", "gpt-5.6-terra"), responses.map { it.metadata.model })
    }

    private class FakeBridge(
        private val completion: CodexAppServerChatResult = CodexAppServerChatResult("model", ""),
        chunks: List<CodexAppServerChatChunk> = emptyList(),
    ) : CodexAppServerChatBridge {
        var completedPrompt: Prompt? = null
        var streamedPrompt: Prompt? = null
        private val chunks = chunks

        override suspend fun complete(
            prompt: Prompt,
            cancellationToken: CancellationToken?,
            toolCallbacks: List<ToolCallback>,
        ): CodexAppServerChatResult {
            completedPrompt = prompt
            return completion
        }

        override fun stream(
            prompt: Prompt,
            cancellationToken: CancellationToken?,
            toolCallbacks: List<ToolCallback>,
        ): Flow<CodexAppServerChatChunk> {
            streamedPrompt = prompt
            return flowOf(*chunks.toTypedArray())
        }
    }
}
