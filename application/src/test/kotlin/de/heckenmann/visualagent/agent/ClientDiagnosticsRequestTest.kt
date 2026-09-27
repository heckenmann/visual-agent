package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ClientDataRequester
import de.heckenmann.visualagent.agent.tools.ProcessInventoryRequest
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.ClientDataRequestPort
import de.heckenmann.visualagent.protocol.ClientProcessEntry
import de.heckenmann.visualagent.protocol.ClientProcessInventoryRequest
import de.heckenmann.visualagent.protocol.ClientProcessInventorySnapshot
import de.heckenmann.visualagent.protocol.ClientRuntimeSnapshot
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class ClientDiagnosticsRequestTest {
    @Test
    fun `client diagnostics are collected only after a server tool requests them`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            var capturedRequest: ChatRequestContext? = null
            every { provider.streamReactive(any<ChatRequestContext>()) } answers {
                capturedRequest = firstArg()
                Flux.just(ChatResponse(model = "test", message = Message("assistant", "ok"), done = true))
            }
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val runtimeSnapshot =
                ClientRuntimeSnapshot(
                    processId = 42,
                    osName = "Test Client OS",
                    osVersion = "1",
                    architecture = "test-arch",
                    availableProcessors = 2,
                    javaVersion = "24",
                    jvmVendor = "Test Vendor",
                    vmName = "Test VM",
                    uptimeMillis = 10,
                    heapUsedBytes = 100,
                    heapCommittedBytes = 200,
                    heapMaxBytes = null,
                    totalPhysicalMemoryBytes = null,
                    freePhysicalMemoryBytes = null,
                    processCpuLoad = null,
                )
            val processSnapshot =
                ClientProcessInventorySnapshot(
                    offset = 1,
                    totalProcesses = 5,
                    processes = listOf(ClientProcessEntry(99, 1, null, "client --token private", "client", listOf("--token", "private"))),
                    hasMore = true,
                )
            var runtimeRequests = 0
            var processRequests = 0
            var capturedProcessRequest: ClientProcessInventoryRequest? = null
            val clientDataRequestPort =
                object : ClientDataRequestPort {
                    override fun requestRuntimeSnapshot(): ClientRuntimeSnapshot {
                        runtimeRequests++
                        return runtimeSnapshot
                    }

                    override fun requestProcessInventory(request: ClientProcessInventoryRequest): ClientProcessInventorySnapshot {
                        processRequests++
                        capturedProcessRequest = request
                        return processSnapshot
                    }
                }

            manager.streamMessage(
                "diagnose client",
                onChunk = {},
                userEntryId = USER_ID,
                assistantEntryId = ASSISTANT_ID,
                clientDataRequester = clientDataRequestPort,
            )

            val requester = capturedRequest?.metadata?.get(ClientDataRequester.METADATA_KEY) as? ClientDataRequester
            assertEquals(0, runtimeRequests)
            assertEquals(0, processRequests)
            assertEquals("Test Client OS", requester?.requestRuntimeReport()?.osName)
            assertEquals(
                "client --token private",
                requester
                    ?.requestProcessInventoryReport(ProcessInventoryRequest("list", 1, 1, null))
                    ?.processes
                    ?.singleOrNull()
                    ?.commandLine,
            )
            assertEquals(1, runtimeRequests)
            assertEquals(1, processRequests)
            assertEquals(ClientProcessInventoryRequest("list", 1, 1, null), capturedProcessRequest)
            assertFalse(
                manager.getHistory().any {
                    it.content.contains("Test Client OS") ||
                        it.metadata.orEmpty().contains(ClientDataRequester.METADATA_KEY) ||
                        it.content.contains("client --token private")
                },
            )
            db.close()
        }

    @Test
    fun `completed client process tool result persists without retaining the requester`() {
        val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
        val manager = AgentManager(db, mockk(relaxed = true), AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
        val requester = mockk<ClientDataRequester>()
        val now = Instant.EPOCH
        manager.recordToolCall(
            ToolCallEvent(
                toolId = "system:client-processes",
                functionName = "system_client_processes",
                phase = ToolCallPhase.FINISHED,
                inputJson = """{"action":"show","pid":42}""",
                context = mapOf("sessionId" to "main", ClientDataRequester.METADATA_KEY to requester),
                result = ToolResult("system:client-processes", true, "client --token secret"),
                startedAtUtc = now,
                finishedAtUtc = now,
                durationMillis = 0,
            ),
        )

        val stored = db.getConversationMessages("main", 10).single { it.role == "tool" }
        assertTrue(stored.metadata.orEmpty().contains("client --token secret"))
        assertFalse(stored.metadata.orEmpty().contains(ClientDataRequester.METADATA_KEY))
        db.close()
    }

    private companion object {
        const val USER_ID = "11111111-1111-4111-8111-111111111111"
        const val ASSISTANT_ID = "22222222-2222-4222-8222-222222222222"
    }
}
