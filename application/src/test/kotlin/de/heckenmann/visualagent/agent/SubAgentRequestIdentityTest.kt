package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.MemoryStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies trusted conversation identity across every sub-agent provider path. */
class SubAgentRequestIdentityTest {
    @ParameterizedTest
    @ValueSource(strings = ["chat", "todo", "stream", "fallback"])
    fun `provider requests retain the owning conversation request`(mode: String) =
        runTest {
            val requests = mutableListOf<ChatRequestContext>()
            val provider = mockk<LLMProvider>()
            val response = ChatResponse("test", Message("assistant", "complete"), true)
            every { provider.chatReactive(any<ChatRequestContext>()) } answers {
                requests += firstArg<ChatRequestContext>()
                Mono.just(response)
            }
            every { provider.streamReactive(any<ChatRequestContext>()) } answers {
                requests += firstArg<ChatRequestContext>()
                if (mode == "fallback") Flux.error(UnsupportedOperationException("Streaming unavailable")) else Flux.just(response)
            }
            val agent = SubAgent("worker", "Worker", "Implementation")
            if (mode == "chat") {
                agent.chat(listOf(Message("user", "work")), provider, requestId = "registered-request")
            } else {
                agent.performTodo(
                    "todo-1",
                    "work",
                    provider,
                    mockk<MemoryStore>(relaxed = true),
                    onChunk = if (mode == "todo") null else { _: String -> },
                    requestId = "registered-request",
                )
            }

            assertEquals(if (mode == "fallback") 2 else 1, requests.size)
            assertTrue(requests.all { it.metadata["requestId"] == "registered-request" })
            assertTrue(requests.all { it.metadata["sessionId"] == "main" && it.metadata["agentId"] == "worker" })
        }
}
