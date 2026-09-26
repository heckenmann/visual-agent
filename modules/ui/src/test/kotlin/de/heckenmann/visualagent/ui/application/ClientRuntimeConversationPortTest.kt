package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ClientRuntimeDiagnosticsPort
import de.heckenmann.visualagent.protocol.ClientRuntimeSnapshot
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClientRuntimeConversationPortTest {
    @Test
    fun `conversation request carries a separate client runtime snapshot`() =
        runBlocking {
            val delegate = mockk<ConversationPort>(relaxed = true)
            var transferredRequest: ConversationStreamRequest? = null
            coEvery { delegate.stream(any(), any(), any()) } coAnswers {
                transferredRequest = firstArg()
                ConversationStreamResult(ConversationMessage("assistant", "ok"))
            }
            val clientPort = ClientRuntimeConversationPort(delegate, ClientRuntimeDiagnosticsPort { sampleSnapshot() })

            clientPort.stream(request(), CancellationTokenImpl()) {}

            assertEquals(sampleSnapshot(), transferredRequest?.clientRuntime)
            assertEquals("hello", transferredRequest?.content)
        }

    @Test
    fun `unavailable diagnostics do not prevent conversation requests`() =
        runBlocking {
            val delegate = mockk<ConversationPort>(relaxed = true)
            var transferredRequest: ConversationStreamRequest? = null
            coEvery { delegate.stream(any(), any(), any()) } coAnswers {
                transferredRequest = firstArg()
                ConversationStreamResult(ConversationMessage("assistant", "ok"))
            }
            val clientPort = ClientRuntimeConversationPort(delegate, ClientRuntimeDiagnosticsPort { error("unavailable") })

            clientPort.stream(request(), CancellationTokenImpl()) {}

            assertNull(transferredRequest?.clientRuntime)
        }

    private fun request() =
        ConversationStreamRequest(
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222",
            "hello",
        )

    private fun sampleSnapshot() =
        ClientRuntimeSnapshot(
            processId = 42,
            osName = "Test OS",
            osVersion = "1",
            architecture = "test-arch",
            availableProcessors = 2,
            javaVersion = "24",
            jvmVendor = "Test Vendor",
            vmName = "Test VM",
            uptimeMillis = 10,
            heapUsedBytes = 100,
            heapCommittedBytes = 200,
            heapMaxBytes = 300,
            totalPhysicalMemoryBytes = null,
            freePhysicalMemoryBytes = null,
            processCpuLoad = null,
        )
}
