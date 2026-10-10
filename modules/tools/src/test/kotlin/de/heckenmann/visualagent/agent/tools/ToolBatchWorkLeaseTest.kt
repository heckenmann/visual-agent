package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolBatchSafety
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies cancellation retains admission for non-cooperative synchronous tools. */
class ToolBatchWorkLeaseTest {
    @Test
    fun `cancelled synchronous work holds global and request permits until it returns`() {
        verifyWorkLifetime(false)
    }

    @Test
    fun `timed out synchronous work holds global and request permits until it returns`() {
        verifyWorkLifetime(true)
    }

    private fun verifyWorkLifetime(timeout: Boolean) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val calls = AtomicInteger()
        val admission = Semaphore(1)
        val tool =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("held"), "held", "Test", "{}", ToolBatchSafety.READ_ONLY_PARALLEL)

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult {
                    if (calls.incrementAndGet() == 1) {
                        entered.countDown()
                        while (release.count > 0) {
                            try {
                                release.await()
                            } catch (_: InterruptedException) {
                                // Simulate non-interruptible native work.
                            }
                        }
                        returned.countDown()
                    } else {
                        secondEntered.countDown()
                    }
                    return success("held", "done")
                }
            }
        val executor = ToolBatchExecutor(ToolRegistry(listOf(tool), ToolEventBus()), globalConcurrency = 1)
        val request =
            ToolBatchRequest(
                listOf(ToolBatchItem("one", "held", JsonObject(emptyMap()))),
                setOf("held"),
                context = mapOf("batchAdmission" to admission),
                limits = ToolBatchLimits(maxConcurrency = 1),
            )
        val first =
            executor
                .execute(
                    if (timeout) request.copy(limits = request.limits.copy(timeoutMillis = 200)) else request,
                ).toFuture()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            if (timeout) {
                assertEquals(
                    de.heckenmann.visualagent.agent.tools.api.ToolErrorCode.TIMEOUT,
                    checkNotNull(first.get(5, TimeUnit.SECONDS)).single().errorCode,
                )
            } else {
                first.cancel(true)
            }
            assertEquals(0, admission.availablePermits())
            val second = executor.execute(request.copy(context = emptyMap())).toFuture()
            try {
                assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS))
                assertEquals(1, calls.get())
                release.countDown()
                assertTrue(returned.await(5, TimeUnit.SECONDS))
                assertTrue(checkNotNull(second.get(5, TimeUnit.SECONDS)).single().success)
                assertEquals(2, calls.get())
                assertEquals(1, admission.availablePermits())
            } finally {
                second.cancel(true)
            }
        } finally {
            release.countDown()
            first.cancel(true)
        }
    }
}
