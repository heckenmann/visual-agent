package de.heckenmann.visualagent.agent.tools

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracerouteToolsTest {
    @Test
    fun `diagnostic child process does not inherit credentials or proxy settings`() {
        val environment =
            diagnosticChildEnvironment(
                mapOf(
                    "PATH" to "/usr/bin",
                    "SystemRoot" to "C:\\Windows",
                    "OPENAI_API_KEY" to "secret",
                    "HTTPS_PROXY" to "http://user:password@proxy",
                    "HOME" to "/Users/private",
                ),
            )

        assertEquals(mapOf("PATH" to "/usr/bin", "SystemRoot" to "C:\\Windows", "LC_ALL" to "C"), environment)
    }

    @Test
    fun `platform detection accepts supported operating systems only`() {
        assertEquals(TraceroutePlatform.LINUX, TraceroutePlatform.fromOsName("Linux"))
        assertEquals(TraceroutePlatform.MACOS, TraceroutePlatform.fromOsName("Mac OS X"))
        assertEquals(TraceroutePlatform.MACOS, TraceroutePlatform.fromOsName("Darwin"))
        assertEquals(TraceroutePlatform.WINDOWS, TraceroutePlatform.fromOsName("Windows 11"))
        assertEquals(null, TraceroutePlatform.fromOsName("Unknown OS"))
    }

    @Test
    fun `command builder creates fixed platform-specific arguments`() {
        val ipv4 = InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 10))
        val ipv6 = InetAddress.getByName("2001:db8::1")

        assertEquals(
            listOf("traceroute", "-n", "-q", "1", "-m", "15", "-w", "1", "-4", "192.0.2.10"),
            TracerouteCommandBuilder.build(TraceroutePlatform.LINUX, ipv4, 15, 1_000),
        )
        assertEquals("-6", TracerouteCommandBuilder.build(TraceroutePlatform.LINUX, ipv6, 15, 1_000)[8])
        assertEquals("traceroute6", TracerouteCommandBuilder.build(TraceroutePlatform.MACOS, ipv6, 15, 1_000).first())
        assertEquals(
            listOf("tracert", "/d", "/h", "15", "/w", "500", "/4", "192.0.2.10"),
            TracerouteCommandBuilder.build(TraceroutePlatform.WINDOWS, ipv4, 15, 500),
        )
    }

    @Test
    fun `parser normalizes responding and silent hop fixtures`() {
        val output =
            """
            traceroute to 192.0.2.10, 3 hops max
             1  192.0.2.1  1.234 ms
             2  *
             3  192.0.2.10  <1 ms
            Trace complete.
            """.trimIndent()

        val hops = TracerouteOutputParser.parse(output, 3)

        assertEquals(3, hops.size)
        assertEquals(TracerouteHop(1, "192.0.2.1", listOf(1.234), "responded"), hops[0])
        assertEquals(TracerouteHop(2, null, emptyList(), "no_response"), hops[1])
        assertEquals("192.0.2.10", hops[2].address)
        assertEquals(listOf(1.0), hops[2].latenciesMillis)
    }

    @Test
    fun `parser ignores malformed numeric addresses and hops above the requested maximum`() {
        val hops =
            TracerouteOutputParser.parse(
                " 1  999.999.999.999  2 ms\n 2  192.0.2.2  3 ms",
                1,
            )

        assertEquals(1, hops.size)
        assertEquals(null, hops.single().address)
    }

    @Test
    fun `probe resolves family before executing and returns normalized destination`() {
        var command: List<String>? = null
        var timeoutMillis = 0L
        val resolver = fakeResolver { listOf(address("192.0.2.20"), address("192.0.2.10")) }
        val runner =
            DiagnosticProcessRunner { args, timeout, _ ->
                command = args
                timeoutMillis = timeout
                DiagnosticProcessResult(0, " 1  192.0.2.10  2.5 ms\n", false, false)
            }
        val probe = JvmTracerouteProbe(resolver, runner, "Linux")

        val result = probe.trace("example.org", "ipv4", 10, 17)

        assertEquals("completed", result.status)
        assertEquals("192.0.2.10", result.resolvedAddress)
        assertTrue(result.destinationReached)
        assertEquals(1, result.hops.single().number)
        assertEquals(17_000L, timeoutMillis)
        assertTrue(command.orEmpty().contains("192.0.2.10"))
        assertTrue(command.orEmpty().contains("-4"))
    }

    @Test
    fun `probe normalizes DNS and missing executable failures`() {
        var runnerCalls = 0
        val runner =
            DiagnosticProcessRunner { _, _, _ ->
                runnerCalls++
                DiagnosticProcessResult(null, "", false, true)
            }
        val dnsFailure = JvmTracerouteProbe(fakeResolver { throw UnknownHostException() }, runner, "Linux")
        val missingExecutable = JvmTracerouteProbe(fakeResolver { listOf(address("192.0.2.10")) }, runner, "Linux")

        assertEquals("dns_failure", dnsFailure.trace("missing.example", "auto", 10, 5).status)
        assertEquals("executable_unavailable", missingExecutable.trace("example.org", "auto", 10, 5).status)
        assertEquals(1, runnerCalls)
    }

    @Test
    fun `probe distinguishes cancellation from an overall timeout`() {
        val runner =
            DiagnosticProcessRunner { _, _, _ ->
                DiagnosticProcessResult(null, "", timedOut = false, executableUnavailable = false, cancelled = true)
            }
        val probe = JvmTracerouteProbe(fakeResolver { listOf(address("192.0.2.10")) }, runner, "Linux")

        assertEquals("cancelled", probe.trace("example.org", "auto", 10, 5).status)
    }

    @Test
    fun `tool rejects invalid input before invoking probe and serializes bounded route`() {
        var probeCalls = 0
        val tool =
            NetworkTracerouteTool(
                TracerouteProbe { _, _, _, _ ->
                    probeCalls++
                    TracerouteResult(
                        "completed",
                        "192.0.2.10",
                        true,
                        listOf(TracerouteHop(1, "192.0.2.10", listOf(1.5), "responded")),
                        3,
                        false,
                    )
                },
            )

        val invalidHost = tool.execute("""{"host":"https://example.org"}""", emptyMap())
        val invalidHops = tool.execute("""{"host":"example.org","maxHops":31}""", emptyMap())
        val valid = tool.execute("""{"host":"example.org","maxHops":1}""", emptyMap())

        assertFalse(invalidHost.success)
        assertFalse(invalidHops.success)
        assertTrue(valid.success)
        assertTrue(valid.content.contains("\"destinationReached\":true"))
        assertTrue(valid.content.contains("\"latenciesMillis\":[1.5]"))
        assertEquals(1, probeCalls)
    }

    private fun fakeResolver(resolveBlock: () -> List<InetAddress>): HostResolver =
        object : HostResolver {
            override fun resolve(
                host: String,
                dnsServer: DnsServerEndpoint?,
            ): List<InetAddress> = resolveBlock()

            override fun reverse(
                address: InetAddress,
                dnsServer: DnsServerEndpoint?,
            ): List<String> = emptyList()
        }

    private fun address(value: String): InetAddress = InetAddress.getByName(value)
}
