package de.heckenmann.visualagent.agent.tools

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the diagnostics adapter captures explicit Logback events and correlation metadata. */
class ServerLogDiagnosticsPortAdapterTest {
    @Test
    fun `captures recent root logger events and detaches cleanly`() {
        val adapter = ServerLogDiagnosticsPortAdapter()
        adapter.afterPropertiesSet()
        val correlationId = "diagnostic-test-${UUID.randomUUID()}"
        try {
            MDC.put("correlationId", correlationId)
            LoggerFactory.getLogger("diagnostics.capture.test").error("unique diagnostic failure")
            MDC.clear()

            val matches =
                adapter
                    .search(
                        de.heckenmann.visualagent.agent.tools.api
                            .ServerLogQuery(query = "unique diagnostic failure"),
                    ).block()!!

            assertEquals(1, matches.size)
            assertEquals(correlationId, matches.single().correlationId)
            assertTrue(matches.single().logger.contains("diagnostics.capture.test"))
        } finally {
            MDC.clear()
            adapter.destroy()
        }
    }
}
