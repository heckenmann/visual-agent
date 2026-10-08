package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolExecutionScope
import de.heckenmann.visualagent.agent.tools.success
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Tests the worker continuation boundary with explicitly controlled tool completion. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoToolCompletionTest {
    @Test
    fun `worker receives both real results before returning its final answer`() =
        runTest {
            val scope = ToolExecutionScope()
            val first = scope.register("first", true)
            val second = scope.register("second", true)
            val prompts = mutableListOf<String?>()
            val work =
                async {
                    finishTodoToolWork(scope, null) { prompt ->
                        prompts += prompt
                        ChatResponse("test", Message("assistant", if (prompt == null) "Scheduled" else "Finished"), true)
                    }
                }
            runCurrent()
            first.finish(success("first", "First actual result"))
            runCurrent()
            assertFalse(work.isCompleted)
            assertEquals(1, prompts.size)
            second.finish(success("second", "Second actual result"))
            assertEquals("Finished", work.await().message.content)
            assertContains(prompts.last()!!, "First actual result")
            assertContains(prompts.last()!!, "Second actual result")
        }

    @Test
    fun `parent cancellation releases the wait without accepting scheduled work`() =
        runTest {
            val scope = ToolExecutionScope()
            scope.register("background", true)
            val token = CancellationToken()
            val work =
                async {
                    finishTodoToolWork(scope, token) { ChatResponse("test", Message("assistant", "Scheduled"), true) }
                }
            runCurrent()
            token.cancel()
            assertFailsWith<CancellationException> { work.await() }
            assertContains(scope.evidence(), "TOOL_CANCELLED")
        }
}
