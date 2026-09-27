package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ClientProcessEntry
import de.heckenmann.visualagent.protocol.ClientProcessInventoryDiagnosticsPort
import de.heckenmann.visualagent.protocol.ClientProcessInventoryRequest
import de.heckenmann.visualagent.protocol.ClientProcessInventorySnapshot
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClientRuntimeConversationPortTest {
    @Test
    fun `ordinary chat does not collect client diagnostics`() =
        runBlocking {
            val delegate = mockk<ConversationPort>(relaxed = true)
            var transferredRequest: ConversationStreamRequest? = null
            coEvery { delegate.stream(any(), any(), any()) } coAnswers {
                transferredRequest = firstArg()
                ConversationStreamResult(ConversationMessage("assistant", "ok"))
            }
            var snapshotReads = 0
            val lazyClientPort =
                ClientRuntimeConversationPort(
                    delegate,
                    ClientRuntimeDiagnosticsPort {
                        snapshotReads++
                        sampleSnapshot()
                    },
                )
            lazyClientPort.stream(request(), CancellationTokenImpl()) {}

            assertEquals(0, snapshotReads)
            assertEquals("hello", transferredRequest?.content)
            assertEquals(sampleSnapshot(), transferredRequest?.clientDataRequester?.requestRuntimeSnapshot())
            assertEquals(1, snapshotReads)
        }

    @Test
    fun `diagnostic collection failure does not prevent ordinary chat`() =
        runBlocking {
            val delegate = mockk<ConversationPort>(relaxed = true)
            var transferredRequest: ConversationStreamRequest? = null
            coEvery { delegate.stream(any(), any(), any()) } coAnswers {
                transferredRequest = firstArg()
                ConversationStreamResult(ConversationMessage("assistant", "ok"))
            }
            val clientPort = ClientRuntimeConversationPort(delegate, ClientRuntimeDiagnosticsPort { error("unavailable") })

            clientPort.stream(request(), CancellationTokenImpl()) {}

            assertNotNull(transferredRequest?.clientDataRequester)
            assertNull(runCatching { transferredRequest?.clientDataRequester?.requestRuntimeSnapshot() }.getOrNull())
        }

    @Test
    fun `process inventory is collected only after the request-scoped capability is invoked`() =
        runBlocking {
            val delegate = mockk<ConversationPort>(relaxed = true)
            var transferredRequest: ConversationStreamRequest? = null
            coEvery { delegate.stream(any(), any(), any()) } coAnswers {
                transferredRequest = firstArg()
                ConversationStreamResult(ConversationMessage("assistant", "ok"))
            }
            val processes =
                ClientProcessInventorySnapshot(
                    offset = 2,
                    totalProcesses = 5,
                    processes = listOf(ClientProcessEntry(42, 1, null, "agent --token secret", "agent", listOf("--token", "secret"))),
                    hasMore = true,
                )
            var processReadCount = 0
            var capturedProcessRequest: ClientProcessInventoryRequest? = null
            val clientPort =
                ClientRuntimeConversationPort(
                    delegate,
                    ClientRuntimeDiagnosticsPort { null },
                    ClientProcessInventoryDiagnosticsPort { request ->
                        processReadCount++
                        capturedProcessRequest = request
                        processes
                    },
                )

            clientPort.stream(request(), CancellationTokenImpl()) {}

            assertEquals(0, processReadCount)
            val processRequest = ClientProcessInventoryRequest("list", 2, 1, null)
            assertEquals(processes, transferredRequest?.clientDataRequester?.requestProcessInventory(processRequest))
            assertEquals(processRequest, capturedProcessRequest)
            assertEquals(1, processReadCount)
            assertEquals("hello", transferredRequest?.content)
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
