package de.heckenmann.visualagent.agent.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemProcessesToolTest {
    @Test
    fun `list exposes full unfiltered commands with bounded pagination`() {
        var observed: ProcessInventoryRequest? = null
        val tool =
            SystemProcessesTool(
                HostProcessInventoryProbe { request ->
                    observed = request
                    ProcessInventorySnapshot(
                        action = "list",
                        offset = request.offset,
                        totalProcesses = 3,
                        processes =
                            listOf(
                                HostProcessEntry(21, 1, null, "server --api-key raw-secret", "server", listOf("--api-key", "raw-secret")),
                            ),
                        hasMore = true,
                    )
                },
            )

        val result = tool.execute("""{"action":"list","offset":1,"pageSize":1}""", emptyMap())

        assertTrue(result.success)
        assertTrue(observed == ProcessInventoryRequest("list", 1, 1, null))
        assertTrue(result.content.contains("\"hostRole\":\"server\""))
        assertTrue(result.content.contains("\"pid\":21"))
        assertTrue(result.content.contains("server --api-key raw-secret"))
        assertTrue(result.content.contains("\"redacted\":false"))
        assertTrue(result.content.contains("\"hasMore\":true"))
    }

    @Test
    fun `show requires a pid and list bounds page size`() {
        val tool = SystemProcessesTool(HostProcessInventoryProbe { error("The probe should not run") })

        assertFalse(tool.execute("""{"action":"show"}""", emptyMap()).success)
        assertFalse(tool.execute("""{"pageSize":101}""", emptyMap()).success)
        assertFalse(tool.execute("""{"action":"filter"}""", emptyMap()).success)
        assertFalse(tool.execute("""{"action":"filter","query":" "}""", emptyMap()).success)
    }

    @Test
    fun `filter forwards a bounded command query without changing returned command values`() {
        var observed: ProcessInventoryRequest? = null
        val tool =
            SystemProcessesTool(
                HostProcessInventoryProbe { request ->
                    observed = request
                    ProcessInventorySnapshot(
                        action = "filter",
                        offset = request.offset,
                        totalProcesses = 1,
                        processes =
                            listOf(
                                HostProcessEntry(21, null, null, "java --key raw-secret", "java", listOf("--key", "raw-secret")),
                            ),
                        hasMore = false,
                    )
                },
            )

        val result = tool.execute("""{"action":"filter","query":"java","offset":0,"pageSize":1}""", emptyMap())

        assertEquals(ProcessInventoryRequest("filter", 0, 1, null, "java"), observed)
        assertTrue(result.success)
        assertTrue(result.content.contains("java --key raw-secret"))
    }

    @Test
    fun `command filter matches command line and arguments without changing case or content`() {
        val entry = HostProcessEntry(21, null, null, "app --KEY Raw-Secret", "app", listOf("--KEY", "Raw-Secret"))

        assertTrue(entry.matchesCommandQuery("raw-secret"))
        assertTrue(entry.matchesCommandQuery("--key"))
        assertFalse(entry.matchesCommandQuery("unrelated"))
        assertEquals("app --KEY Raw-Secret", entry.commandLine)
    }

    @Test
    fun `filter counts all matches while retaining only the requested page`() {
        val entries =
            (0L until 10_000L).asSequence().map { pid ->
                val command = if (pid % 2L == 0L) "server --pid=$pid" else "worker --pid=$pid"
                HostProcessEntry(pid, null, null, command, command, emptyList())
            }

        val snapshot = filterProcessInventory(entries, ProcessInventoryRequest("filter", 100, 3, null, "SERVER"))

        assertEquals(5_000, snapshot.totalProcesses)
        assertEquals(listOf(200L, 202L, 204L), snapshot.processes.map { it.pid })
        assertEquals("server --pid=200", snapshot.processes.first().commandLine)
        assertTrue(snapshot.hasMore)
    }

    @Test
    fun `JVM probe enumerates process inventory with requested page bound`() {
        val snapshot = JvmHostProcessInventoryProbe().inspect(ProcessInventoryRequest("list", 0, 1, null))
        assertTrue(snapshot.processes.size <= 1)
        assertTrue(snapshot.totalProcesses >= snapshot.processes.size)
    }

    @Test
    fun `client process tool labels the client and preserves unfiltered commands`() {
        var requests = 0
        var observedRequest: ProcessInventoryRequest? = null
        val requester =
            object : ClientDataRequester {
                override fun requestRuntimeReport() = null

                override fun requestProcessInventoryReport(request: ProcessInventoryRequest): ClientProcessInventoryReport {
                    requests++
                    observedRequest = request
                    return ClientProcessInventoryReport(
                        offset = request.offset,
                        totalProcesses = 12,
                        processes =
                            listOf(
                                HostProcessEntry(42, null, null, "client --password secret", "client", listOf("--password", "secret")),
                            ),
                        hasMore = true,
                    )
                }
            }
        val tool = SystemClientProcessesTool()

        assertEquals(0, requests)
        val result =
            tool.execute(
                """{"action":"list","pageSize":1}""",
                mapOf("clientDataRequester" to requester),
            )

        assertEquals(1, requests)
        assertEquals(ProcessInventoryRequest("list", 0, 1, null), observedRequest)
        assertTrue(result.success)
        assertTrue(result.content.contains("\"totalProcesses\":12"))
        assertTrue(result.content.contains("\"hostRole\":\"client\""))
        assertTrue(result.content.contains("client --password secret"))
        assertTrue(result.content.contains("secret"))
        assertFalse(result.content.contains("\"hostRole\":\"server\""))
    }

    @Test
    fun `client process tool fails clearly without a request scoped client inventory`() {
        val result = SystemClientProcessesTool().execute("{}", emptyMap())

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("no request-scoped access"))
    }

    @Test
    fun `client process tool requests the exact pid for show`() {
        val requested = mutableListOf<ProcessInventoryRequest>()
        val pid = 42L
        val requester =
            object : ClientDataRequester {
                override fun requestRuntimeReport() = null

                override fun requestProcessInventoryReport(request: ProcessInventoryRequest): ClientProcessInventoryReport {
                    requested += request
                    return ClientProcessInventoryReport(
                        offset = 0,
                        totalProcesses = 1,
                        processes = listOf(HostProcessEntry(pid, null, null, "client", "client", emptyList())),
                        hasMore = false,
                    )
                }
            }

        val result =
            SystemClientProcessesTool().execute(
                """{"action":"show","pid":$pid}""",
                mapOf(ClientDataRequester.METADATA_KEY to requester),
            )

        assertTrue(result.success)
        assertEquals(listOf(ProcessInventoryRequest("show", 0, 50, pid)), requested)
        assertTrue(result.content.contains("\"pid\":$pid"))
    }

    @Test
    fun `client process tool rejects a response for a different page`() {
        val requester =
            object : ClientDataRequester {
                override fun requestRuntimeReport() = null

                override fun requestProcessInventoryReport(request: ProcessInventoryRequest) =
                    ClientProcessInventoryReport(
                        offset = request.offset + 1,
                        totalProcesses = 1,
                        processes = listOf(HostProcessEntry(42, null, null, "client", "client", emptyList())),
                        hasMore = false,
                    )
            }

        val result =
            SystemClientProcessesTool().execute(
                """{"action":"list","offset":0,"pageSize":1}""",
                mapOf(ClientDataRequester.METADATA_KEY to requester),
            )

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("different page"))
    }
}
