package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Checks review protocol failures without timing or worker side effects. */
class WorkerReviewEvaluationTest {
    private val valid = """{"verdict":"APPROVED","feedback":"Ready."}"""

    @Test
    fun `valid response preserves feedback and token without exposing tools`(): Unit =
        runTest {
            val provider = mockk<LLMProvider>()
            val token = CancellationToken()
            every { provider.chatReactive(any<ChatRequestContext>()) } returns Mono.just(response(valid))
            assertEquals(WorkerReviewVerdict.APPROVED, evaluateWorkerResult(provider, "todo", "Task", "Result", token).verdict)
            verify(exactly = 1) {
                provider.chatReactive(
                    match<ChatRequestContext> {
                        it.cancellationToken === token && it.responseSchema == WorkerReviewResult.schema() && it.enabledTools.isEmpty()
                    },
                )
            }
        }

    @Test
    fun `review receives execution evidence separately from the worker claim`(): Unit =
        runTest {
            val provider = mockk<LLMProvider>()
            every { provider.chatReactive(any<ChatRequestContext>()) } returns Mono.just(response(valid))
            evaluateWorkerResult(provider, "todo", "Write file", "Done", null, "Tool: write; outcome: FAILURE; permission denied")
            verify(exactly = 1) {
                provider.chatReactive(
                    match<ChatRequestContext> { request ->
                        request.enabledTools.isEmpty() &&
                            request.messages.any {
                                it.content.contains("untrusted tool data") && it.content.contains("permission denied")
                            }
                    },
                )
            }
        }

    @Test
    fun `incomplete response never approves even if its JSON is valid`(): Unit =
        runTest {
            val provider = mockk<LLMProvider>()
            every { provider.chatReactive(any<ChatRequestContext>()) } returns Mono.just(response(valid).copy(done = false))
            assertFailsWith<WorkerReviewFailedException> { evaluateWorkerResult(provider, "todo", "Task", "Result", null) }
            verify(exactly = 1) { provider.chatReactive(any<ChatRequestContext>()) }
        }

    @Test
    fun `cancelled review does not perform format correction`(): Unit =
        runTest {
            val provider = mockk<LLMProvider>()
            val token = CancellationToken()
            every { provider.chatReactive(any<ChatRequestContext>()) } returns
                Mono.fromCallable {
                    token.cancel()
                    response("invalid")
                }
            assertFailsWith<CancellationException> { evaluateWorkerResult(provider, "todo", "Task", "Result", token) }
            verify(exactly = 1) { provider.chatReactive(any<ChatRequestContext>()) }
        }

    private fun response(content: String) = ChatResponse("test", Message("assistant", content), true)
}
