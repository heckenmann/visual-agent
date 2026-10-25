package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.model.ToolLiveSeries
import de.heckenmann.visualagent.model.ToolLiveState
import reactor.test.scheduler.VirtualTimeScheduler
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ToolLiveSessionTest {
    @Test
    fun `status and deadline replace values while explicit log entries remain in the snapshot`() {
        val session = ToolLiveSession(publish = {})
        session.report(state("Waiting 12").copy(deadlineEpochMillis = 12000))
        session.report(state("Waiting 11").copy(deadlineEpochMillis = 11000))
        assertTrue(session.snapshot()!!.logs.isEmpty())
        assertEquals("Waiting 11", session.snapshot()!!.status)
        assertEquals(11000L, session.snapshot()!!.deadlineEpochMillis)
        session.report(state("Running").copy(logs = listOf("First output")))
        session.report(state("Running").copy(logs = listOf("First output", "Second output")))
        assertEquals(listOf("First output", "Second output"), session.finish()!!.logs)
    }

    @Test
    fun `first update is immediate and subsequent snapshots coalesce under virtual time`() {
        val clock = VirtualTimeScheduler.create()
        val events = mutableListOf<ToolLiveState>()
        val session = ToolLiveSession(clock, events::add)
        session.start()
        session.report(state("First"))
        repeat(100) { session.report(state("Update $it")) }
        assertEquals(listOf("First"), events.map { it.status })
        clock.advanceTimeBy(Duration.ofMillis(100))
        assertEquals(listOf("First", "Update 99"), events.map { it.status })
        val final = session.finish()!!
        assertTrue(final.terminal)
        assertTrue(final.revision > events.last().revision)
        session.report(state("Too late"))
        clock.advanceTimeBy(Duration.ofDays(1))
        assertEquals(2, events.size)
        assertEquals(final, session.finish())
        clock.dispose()
    }

    @Test
    fun `each invocation owns its identity and bounded sanitized snapshots`() {
        val first = ToolLiveSession(publish = {})
        val second = ToolLiveSession(publish = {})
        first.report(
            state("token=private-value").copy(
                logs = List(100) { "x".repeat(1000) },
                series =
                    listOf(
                        ToolLiveSeries(
                            "Samples",
                            List(200) { it.toDouble() } + Double.NaN,
                        ),
                    ),
            ),
        )
        second.report(state("Other"))
        val result = first.finish()!!
        assertNotEquals(result.executionId, second.finish()!!.executionId)
        assertFalse(result.status.contains("private-value"))
        assertEquals(40, result.logs.size)
        assertTrue(result.logs.all { it.length == 500 })
        assertEquals(
            127,
            result.series
                .single()
                .values.size,
        )
        assertTrue(
            result.series
                .single()
                .values
                .all(Double::isFinite),
        )
    }

    private fun state(status: String) = ToolLiveState("untrusted-id", 900, status, terminal = true)
}
