package de.heckenmann.visualagent.agent.tools

import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmPingProbeTest {
    @Test
    fun `tool delegates normalized host and bounded settings and explains JVM method`() {
        var request: List<Any>? = null
        val tool =
            NetworkPingTool(
                PingProbe { host, family, count, timeout ->
                    request = listOf(host, family, count, timeout)
                    PingProbeResult("completed", "192.0.2.10", 2, 1, 50.0, 4.0, 4.0, 4.0, 8, false)
                },
            )

        val result =
            tool.execute(
                """{"host":"Example.org.","family":"ipv4","count":2,"timeoutSeconds":3}""",
                emptyMap(),
            )

        assertTrue(result.success)
        assertEquals(listOf("example.org", "ipv4", 2, 3), request)
        assertTrue(result.content.contains("InetAddress.isReachable"))
        assertTrue(result.content.contains("\"unansweredAttemptsPercent\":50.0"))
    }

    @Test
    fun `tool rejects invalid host family count and timeout before probing`() {
        var probeCalls = 0
        val tool =
            NetworkPingTool(
                PingProbe { _, _, _, _ ->
                    probeCalls++
                    PingProbeResult("completed", null, 0, 0, null, null, null, null, 0, false)
                },
            )

        val invalidHost = tool.execute("""{"host":"https://example.org/path"}""", emptyMap())
        val invalidFamily = tool.execute("""{"host":"example.org","family":"all"}""", emptyMap())
        val invalidCount = tool.execute("""{"host":"example.org","count":11}""", emptyMap())
        val invalidTimeout = tool.execute("""{"host":"example.org","timeoutSeconds":31}""", emptyMap())

        assertFalse(invalidHost.success)
        assertFalse(invalidFamily.success)
        assertFalse(invalidCount.success)
        assertFalse(invalidTimeout.success)
        assertEquals(0, probeCalls)
    }

    @Test
    fun `probe selects deterministic address in requested family and aggregates successes`() {
        val ipv4High = address("192.0.2.20")
        val ipv4Low = address("192.0.2.10")
        val ipv6 = address("2001:db8::1")
        val clock = FakeMonotonicTimeSource()
        val checked = mutableListOf<InetAddress>()
        val probe =
            JvmPingProbe(
                resolver = resolver { listOf(ipv4High, ipv6, ipv4Low) },
                reachabilityChecker =
                    ReachabilityChecker { target, _ ->
                        checked += target
                        clock.advanceMillis(4)
                        true
                    },
                timeSource = clock,
            )

        val result = probe.ping("example.org", "ipv4", 2, 1)

        assertEquals("192.0.2.10", result.resolvedAddress)
        assertEquals(listOf(ipv4Low, ipv4Low), checked)
        assertEquals("completed", result.status)
        assertEquals(2, result.attemptsCompleted)
        assertEquals(2, result.reachableResponses)
        assertEquals(0.0, result.unansweredPercent)
        assertEquals(4.0, result.averageRttMillis)
    }

    @Test
    fun `probe handles unknown host and unavailable address family`() {
        val clock = FakeMonotonicTimeSource()
        val checker = ReachabilityChecker { _, _ -> error("must not probe") }
        val unavailable = JvmPingProbe(resolver { listOf(address("192.0.2.10")) }, checker, clock)
        val unresolved = JvmPingProbe(resolver { throw UnknownHostException() }, checker, clock)

        assertEquals("family_unavailable", unavailable.ping("example.org", "ipv6", 2, 1).status)
        val unresolvedResult = unresolved.ping("example.org", "auto", 2, 1)
        assertEquals("dns_failure", unresolvedResult.status)
        assertEquals(null, unresolvedResult.resolvedAddress)
    }

    @Test
    fun `probe normalizes failed checks and stops when injected deadline is reached`() {
        val clock = FakeMonotonicTimeSource()
        var checks = 0
        val probe =
            JvmPingProbe(
                resolver = resolver { listOf(address("192.0.2.10")) },
                reachabilityChecker =
                    ReachabilityChecker { _, timeoutMillis ->
                        checks++
                        assertTrue(timeoutMillis > 0)
                        clock.advanceMillis(1_000)
                        throw IOException("implementation detail")
                    },
                timeSource = clock,
            )

        val result = probe.ping("example.org", "auto", 4, 1)

        assertEquals("no_response", result.status)
        assertEquals(1, result.attemptsCompleted)
        assertEquals(1, checks)
        assertEquals(100.0, result.unansweredPercent)
        assertTrue(result.deadlineReached)
        assertEquals("192.0.2.10", result.resolvedAddress)
    }

    private fun resolver(resolveBlock: () -> List<InetAddress>): HostResolver =
        object : HostResolver {
            override fun resolve(
                host: String,
                dnsServer: DnsServerEndpoint?,
                family: String,
            ): List<InetAddress> = resolveBlock()

            override fun reverse(
                address: InetAddress,
                dnsServer: DnsServerEndpoint?,
            ): List<String> = emptyList()
        }

    private fun address(value: String): InetAddress = InetAddress.getByName(value)

    private class FakeMonotonicTimeSource : MonotonicTimeSource {
        private var now = 0L

        override fun nanoTime(): Long = now

        fun advanceMillis(millis: Long) {
            now += millis * NANOS_PER_MILLI
        }

        private companion object {
            const val NANOS_PER_MILLI = 1_000_000L
        }
    }
}
