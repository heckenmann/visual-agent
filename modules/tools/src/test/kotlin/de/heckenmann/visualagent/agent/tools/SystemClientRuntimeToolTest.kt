package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemClientRuntimeToolTest {
    private val tool = SystemClientRuntimeTool()

    @Test
    fun `client diagnostics fail when the server has no client request capability`() {
        val result = tool.execute("{}")

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("no request-scoped access"))
    }

    @Test
    fun `tool requests client diagnostics on demand and sanitizes descriptive values`() {
        var requests = 0
        val requester =
            object : ClientDataRequester {
                override fun requestRuntimeReport(): ClientRuntimeReport {
                    requests++
                    return sampleSnapshot(osName = "Desktop\nsecret")
                }

                override fun requestProcessInventoryReport(request: ProcessInventoryRequest) = null
            }

        assertEquals(0, requests)
        val result = tool.execute("{}", mapOf("clientDataRequester" to requester))

        assertEquals(1, requests)
        assertTrue(result.success)
        assertTrue(result.data.toString().contains("\"scope\":\"desktop-client-jvm\""))
        assertTrue(result.content.contains("Desktopsecret"))
        assertFalse(result.content.contains('\n'))
        assertTrue(result.content.contains("\"processId\":42"))
    }

    @Test
    fun `invalid client diagnostic values are rejected`() {
        val requester =
            object : ClientDataRequester {
                override fun requestRuntimeReport() = sampleSnapshot(availableProcessors = 0)

                override fun requestProcessInventoryReport(request: ProcessInventoryRequest) = null
            }
        val result = tool.execute("{}", mapOf("clientDataRequester" to requester))

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("invalid or incomplete"))
    }

    private fun sampleSnapshot(
        osName: String = "Test OS",
        availableProcessors: Int = 4,
    ) = ClientRuntimeReport(
        processId = 42,
        osName = osName,
        osVersion = "1",
        architecture = "test-arch",
        availableProcessors = availableProcessors,
        javaVersion = "24",
        jvmVendor = "Test Vendor",
        vmName = "Test VM",
        uptimeMillis = 10,
        heapUsedBytes = 100,
        heapCommittedBytes = 200,
        heapMaxBytes = 300,
        totalPhysicalMemoryBytes = 400,
        freePhysicalMemoryBytes = 200,
        processCpuLoad = 0.5,
    )
}
