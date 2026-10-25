package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.model.ToolLiveState
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolRegistryLiveExecutionTest {
    @Test
    fun `live updates precede completion and retain identity through final state`() {
        val tool = ControlledTool()
        val bus = ToolEventBus()
        val events = CopyOnWriteArrayList<ToolCallEvent>()
        val done = CountDownLatch(1)
        val registration = bus.addListener(events::add)
        val registry = ToolRegistry(listOf(tool), bus)
        val subscription = registry.executeReactive(tool, "{}", mapOf("requestId" to "request")).subscribe { done.countDown() }
        try {
            tool.reported.await()
            assertEquals(listOf(ToolCallPhase.STARTED, ToolCallPhase.UPDATED), events.map { it.phase })
            assertNotNull(events.first().executionId)
            assertEquals(events.first().executionId, registry.liveExecutionSnapshots().single().executionId)
            assertEquals(
                "Running",
                registry
                    .liveExecutionSnapshots()
                    .single()
                    .liveState!!
                    .status,
            )
            assertFalse(registry.cancelLiveExecution(events.first().executionId!!, "wrong-request"))
            tool.result.tryEmitValue(ToolResult("context", true, "Complete"))
            done.await()
            assertEquals(ToolCallPhase.FINISHED, events.last().phase)
            assertEquals(events.first().executionId, events.last().executionId)
            assertTrue(events.last().liveState!!.terminal)
            assertFalse(registry.cancelLiveExecution(events.first().executionId!!, "request"))
            assertTrue(registry.liveExecutionSnapshots().isEmpty())
        } finally {
            subscription.dispose()
            registration.close()
        }
    }

    @Test
    fun `explicit cancellation seals the snapshot and finishes the call`() {
        val tool = ControlledTool()
        val bus = ToolEventBus()
        val events = CopyOnWriteArrayList<ToolCallEvent>()
        val done = CountDownLatch(1)
        val registration = bus.addListener(events::add)
        val registry = ToolRegistry(listOf(tool), bus)
        val subscription = registry.executeReactive(tool, "{}", mapOf("requestId" to "request")).subscribe { done.countDown() }
        try {
            tool.reported.await()
            assertTrue(registry.cancelLiveExecution(events.first().executionId!!, "request"))
            done.await()
            val final = events.last()
            assertEquals(ToolCallPhase.FINISHED, final.phase)
            assertFalse(final.result.success)
            assertTrue(final.liveState!!.terminal)
            assertEquals("Cancelled", final.liveState.status)
        } finally {
            subscription.dispose()
            registration.close()
        }
    }

    @Test
    fun `failure before the first snapshot still closes the original execution`() {
        val tool = ControlledTool(report = false)
        val bus = ToolEventBus()
        val events = CopyOnWriteArrayList<ToolCallEvent>()
        val done = CountDownLatch(1)
        val registration = bus.addListener(events::add)
        val registry = ToolRegistry(listOf(tool), bus)
        val subscription = registry.executeReactive(tool, "{}", emptyMap()).subscribe { done.countDown() }
        try {
            tool.reported.await()
            tool.result.tryEmitError(IllegalArgumentException("Invalid input"))
            done.await()
            assertEquals(listOf(ToolCallPhase.STARTED, ToolCallPhase.FINISHED), events.map { it.phase })
            assertNotNull(events.first().executionId)
            assertEquals(events.first().executionId, events.last().executionId)
            assertFalse(events.last().result.success)
        } finally {
            subscription.dispose()
            registration.close()
        }
    }

    private class ControlledTool(
        private val report: Boolean = true,
    ) : LiveVisualAgentTool {
        override val definition = ToolDefinition(ToolId("context"), "context", "Test", "{}")
        val result = Sinks.one<ToolResult>()
        val reported = CountDownLatch(1)

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = error("Live capability was not discovered")

        override fun executeLiveReactive(
            inputJson: String,
            context: Map<String, Any>,
            reporter: ToolLiveReporter,
        ): Mono<ToolResult> {
            if (report) reporter.report(ToolLiveState("", 0, "Running"))
            reported.countDown()
            return result.asMono()
        }
    }
}
