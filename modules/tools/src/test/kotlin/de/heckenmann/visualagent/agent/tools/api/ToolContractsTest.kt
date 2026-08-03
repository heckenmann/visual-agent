package de.heckenmann.visualagent.agent.tools.api

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the provider-neutral tool contracts and lifecycle event bus.
 */
class ToolContractsTest {
    /**
     * Publishes lifecycle events to registered listeners and supports removal.
     */
    @Test
    fun `event bus publishes and removes listeners`() {
        val bus = ToolEventBus()
        val received = mutableListOf<ToolCallEvent>()
        val handle = bus.addListener(received::add)
        val event = event(ToolCallPhase.STARTED)

        bus.publish(event)
        handle.close()
        bus.publish(event(ToolCallPhase.FINISHED))

        assertEquals(listOf(event), received)
    }

    /**
     * Keeps the stable tool identifier and result payload intact.
     */
    @Test
    fun `contracts preserve tool identity and result`() {
        val id = ToolId("file:read")
        val definition = ToolDefinition(id, "file_read", "Read a file", "{}")
        val result = ToolResult(id.value, success = true, content = "content")

        assertEquals("file:read", definition.id.value)
        assertTrue(result.success)
        assertEquals("content", result.content)
    }

    private fun event(phase: ToolCallPhase) =
        ToolCallEvent(
            toolId = "file:read",
            functionName = "file_read",
            phase = phase,
            inputJson = "{}",
            context = emptyMap(),
            result = ToolResult("file:read", success = true, content = ""),
            startedAtUtc = Instant.EPOCH,
            finishedAtUtc = Instant.EPOCH,
            durationMillis = 0,
        )
}
