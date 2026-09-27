package de.heckenmann.visualagent.agent.tools

import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerDiagnosticsToolsTest {
    @Test
    fun `server time reports deterministic UTC local and requested zone`() {
        val clock = Clock.fixed(Instant.parse("2026-09-26T10:15:30Z"), ZoneId.of("Europe/Berlin"))

        val result = ServerTimeTool(clock).execute("""{"zoneId":"Asia/Tokyo"}""", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("2026-09-26T10:15:30Z"))
        assertTrue(result.content.contains("2026-09-26T12:15:30+02:00"))
        assertTrue(result.content.contains("Europe/Berlin"))
        assertTrue(result.content.contains("2026-09-26T19:15:30+09:00"))
        assertTrue(result.content.contains("epochMilliseconds"))
    }

    @Test
    fun `server time rejects invalid requested time zone`() {
        val result = ServerTimeTool(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).execute("""{"zoneId":"Not/A_Zone"}""", emptyMap())

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("Invalid zoneId"))
    }

    @Test
    fun `dns combines sorted deduplicated families and passes explicit dns server`() {
        var selectedServer: DnsServerEndpoint? = null
        val ipv4First = InetAddress.getByName("192.0.2.20")
        val ipv4Second = InetAddress.getByName("192.0.2.10")
        val ipv6 = InetAddress.getByName("2001:db8::1")
        val resolver =
            fakeResolver(
                resolve = { host, dnsServer ->
                    assertEquals("example.org", host)
                    selectedServer = dnsServer
                    listOf(ipv4First, ipv6, ipv4Second, ipv4First)
                },
            )

        val result =
            NetworkDnsTool(resolver).execute(
                """{"host":"Example.org.","family":"all","dnsServer":"192.0.2.53","dnsPort":5353}""",
                emptyMap(),
            )

        assertTrue(result.success)
        assertEquals("192.0.2.53", selectedServer?.address?.hostAddress)
        assertEquals(5353, selectedServer?.port)
        assertTrue(result.content.contains("192.0.2.10"))
        assertTrue(result.content.indexOf("192.0.2.10") < result.content.indexOf("192.0.2.20"))
        assertTrue(result.content.contains("2001:db8:0:0:0:0:0:1"))
        assertTrue(result.content.contains("192.0.2.53:5353"))
    }

    @Test
    fun `dns filters requested address family`() {
        val resolver = fakeResolver(resolve = { _, _ -> listOf(InetAddress.getByName("192.0.2.10"), InetAddress.getByName("2001:db8::1")) })

        val result = NetworkDnsTool(resolver).execute("""{"host":"example.org","family":"ipv6"}""", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("2001:db8:0:0:0:0:0:1"))
        assertFalse(result.content.contains("192.0.2.10"))
    }

    @Test
    fun `dns server formats ipv6 address with brackets`() {
        val resolver = fakeResolver()

        val result =
            NetworkDnsTool(resolver).execute(
                """{"host":"example.org","dnsServer":"2001:db8::53","dnsPort":5353}""",
                emptyMap(),
            )

        assertTrue(result.success)
        assertTrue(result.content.contains("[2001:db8:0:0:0:0:0:53]:5353"))
    }

    @Test
    fun `dns rejects urls and invalid dns server without resolving`() {
        var resolverCalled = false
        val resolver =
            fakeResolver(resolve = { _, _ ->
                resolverCalled = true
                emptyList()
            })

        val urlResult = NetworkDnsTool(resolver).execute("""{"host":"https://example.org/path"}""", emptyMap())
        val serverResult = NetworkDnsTool(resolver).execute("""{"host":"example.org","dnsServer":"resolver.example.org"}""", emptyMap())
        val portOnlyResult = NetworkDnsTool(resolver).execute("""{"host":"example.org","dnsPort":5353}""", emptyMap())

        assertFalse(urlResult.success)
        assertFalse(serverResult.success)
        assertFalse(portOnlyResult.success)
        assertFalse(resolverCalled)
    }

    @Test
    fun `forward and reverse DNS are exposed as different provider functions`() {
        val resolver = fakeResolver()

        assertEquals("network:dns", NetworkDnsTool(resolver).definition.id.value)
        assertEquals("network_dns", NetworkDnsTool(resolver).definition.name)
        assertEquals("network:reverse-dns", NetworkReverseDnsTool(resolver).definition.id.value)
        assertEquals("network_reverse_dns", NetworkReverseDnsTool(resolver).definition.name)
        assertFalse(NetworkDnsTool(resolver).definition.inputSchema.contains("reverse"))
    }

    @Test
    fun `dns normalizes resolver failures without exposing exception details`() {
        val resolver = fakeResolver(resolve = { _, _ -> throw UnknownHostException("private resolver detail") })

        val result = NetworkDnsTool(resolver).execute("""{"host":"missing.example"}""", emptyMap())

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("could not resolve"))
        assertFalse(result.error.orEmpty().contains("private resolver detail"))
    }

    @Test
    fun `separate reverse dns tool returns ptr names through selected server`() {
        var reverseAddress: InetAddress? = null
        var selectedServer: DnsServerEndpoint? = null
        val resolver =
            fakeResolver(
                reverse = { address, dnsServer ->
                    reverseAddress = address
                    selectedServer = dnsServer
                    listOf("HOST.Example.", "host.example.")
                },
            )

        val result =
            NetworkReverseDnsTool(resolver).execute(
                """{"address":"192.0.2.10","dnsServer":"192.0.2.53"}""",
                emptyMap(),
            )

        assertTrue(result.success)
        assertEquals("network:reverse-dns", NetworkReverseDnsTool(resolver).definition.id.value)
        assertEquals("network:dns", NetworkDnsTool(resolver).definition.id.value)
        assertEquals("192.0.2.10", reverseAddress?.hostAddress)
        assertEquals("192.0.2.53", selectedServer?.address?.hostAddress)
        assertTrue(result.content.contains("host.example"))
        assertFalse(result.content.contains("HOST.Example"))
    }

    @Test
    fun `dns reverse lookup rejects hostnames`() {
        var resolverCalled = false
        val resolver =
            fakeResolver(reverse = { _, _ ->
                resolverCalled = true
                emptyList()
            })

        val result = NetworkReverseDnsTool(resolver).execute("""{"address":"example.org"}""", emptyMap())

        assertFalse(result.success)
        assertFalse(resolverCalled)
    }

    @Test
    fun `tcp tool reports one normalized endpoint result`() {
        var request: Triple<String, Int, String>? = null
        val probe =
            TcpConnectionProbe { host, port, family ->
                request = Triple(host, port, family)
                TcpProbeOutcome(
                    resolvedAddresses = listOf("192.0.2.10"),
                    connectedAddress = "192.0.2.10",
                    connected = true,
                    durationMillis = 14,
                    failureCategory = null,
                )
            }

        val result = NetworkTcpTool(probe).execute("""{"host":"Example.org.","port":443}""", emptyMap())

        assertTrue(result.success)
        assertEquals(Triple("example.org", 443, "auto"), request)
        assertTrue(result.content.contains("\"connected\":true"))
        assertTrue(result.content.contains("\"connectedAddress\":\"192.0.2.10\""))
    }

    @Test
    fun `tcp tool returns normalized diagnostic failures`() {
        val probe =
            TcpConnectionProbe { _, _, _ ->
                TcpProbeOutcome(
                    resolvedAddresses = listOf("192.0.2.10"),
                    connectedAddress = null,
                    connected = false,
                    durationMillis = 12,
                    failureCategory = "connection_refused",
                )
            }

        val result = NetworkTcpTool(probe).execute("""{"host":"example.org","port":443}""", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("connection_refused"))
        assertFalse(result.content.contains("Exception"))
    }

    @Test
    fun `tcp tool rejects invalid target and family before connecting`() {
        var probeCalled = false
        val probe =
            TcpConnectionProbe { _, _, _ ->
                probeCalled = true
                TcpProbeOutcome(emptyList(), null, false, 0, "connection_failed")
            }

        val invalidHost = NetworkTcpTool(probe).execute("""{"host":"https://example.org","port":443}""", emptyMap())
        val invalidPort = NetworkTcpTool(probe).execute("""{"host":"example.org","port":70000}""", emptyMap())
        val invalidFamily = NetworkTcpTool(probe).execute("""{"host":"example.org","port":443,"family":"all"}""", emptyMap())

        assertFalse(invalidHost.success)
        assertFalse(invalidPort.success)
        assertFalse(invalidFamily.success)
        assertFalse(probeCalled)
    }

    @Test
    fun `runtime snapshot formatting uses binary units and omits unavailable metrics`() {
        val snapshot =
            RuntimeDiagnosticSnapshot(
                osName = "Test OS",
                osVersion = "1",
                architecture = "test-arch",
                availableProcessors = 4,
                javaVersion = "24",
                jvmVendor = "Test Vendor",
                vmName = "Test VM",
                uptimeMillis = 3_661_000,
                heapUsedBytes = 1024,
                heapCommittedBytes = 2048,
                heapMaxBytes = 4096,
                nonHeapUsedBytes = 512,
                totalPhysicalMemoryBytes = null,
                freePhysicalMemoryBytes = null,
                processCpuLoad = null,
                systemCpuLoad = null,
            )

        val text = snapshot.toContextText()

        assertTrue(text.contains("1h 1m 1s"))
        assertTrue(text.contains("1.0 KiB / 2.0 KiB / 4.0 KiB"))
        assertFalse(text.contains("Physical memory"))
        assertFalse(text.contains("CPU load"))
    }

    private fun fakeResolver(
        resolve: (String, DnsServerEndpoint?) -> List<InetAddress> = { _, _ -> emptyList() },
        reverse: (InetAddress, DnsServerEndpoint?) -> List<String> = { _, _ -> emptyList() },
    ): HostResolver =
        object : HostResolver {
            override fun resolve(
                host: String,
                dnsServer: DnsServerEndpoint?,
                family: String,
            ): List<InetAddress> = resolve(host, dnsServer)

            override fun reverse(
                address: InetAddress,
                dnsServer: DnsServerEndpoint?,
            ): List<String> = reverse(address, dnsServer)
        }
}
