package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.MemoryStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies that a non-streaming fallback replaces partial streamed output. */
class SubAgentStreamingFallbackTest {
    @Test
    fun `performTodo resets the stream before publishing fallback output`() =
        runBlocking {
            val provider = mockk<LLMProvider>()
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flowOf(ChatResponse("test", Message("assistant", "partial"), false))
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse("test", Message("assistant", "complete response"), true)
            val memoryStore = mockk<MemoryStore>(relaxed = true)
            every { memoryStore.saveStructuredKnowledge(any(), any(), any()) } returns "memory-id"
            val events = mutableListOf<String>()

            SubAgent("agent-1", "Coder", "Implementation").performTodo(
                todoId = "todo-1",
                description = "Complete the task",
                provider = provider,
                memoryStore = memoryStore,
                onChunk = { events += "chunk:$it" },
                onStreamReset = { events += "reset" },
            )

            assertEquals(listOf("chunk:partial", "reset", "chunk:complete response"), events)
        }
}
