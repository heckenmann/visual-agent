package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemClientRuntimeToolTest {
    private val tool = SystemClientRuntimeTool()

    @Test
    fun `client diagnostics are available only when explicitly transferred`() {
        val result = tool.execute("{}")

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("were not supplied"))
    }

    @Test
    fun `client diagnostics identify their scope and sanitize descriptive values`() {
        val result = tool.execute("{}", mapOf("clientRuntimeSnapshot" to sampleSnapshot(osName = "Desktop\nsecret")))

        assertTrue(result.success)
        assertTrue(result.data.toString().contains("\"scope\":\"desktop-client-jvm\""))
        assertTrue(result.content.contains("Desktopsecret"))
        assertFalse(result.content.contains('\n'))
        assertTrue(result.content.contains("\"processId\":42"))
    }

    @Test
    fun `invalid client diagnostic values are rejected`() {
        val result = tool.execute("{}", mapOf("clientRuntimeSnapshot" to sampleSnapshot(availableProcessors = 0)))

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
