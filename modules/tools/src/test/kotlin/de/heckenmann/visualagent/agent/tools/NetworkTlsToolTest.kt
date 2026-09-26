package de.heckenmann.visualagent.agent.tools

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkTlsToolTest {
    @Test
    fun `tool validates parameters and returns normalized TLS metadata`() {
        var calls = 0
        val certificate =
            TlsCertificateSummary(
                "CN=example.org",
                "CN=Example CA",
                "2026-01-01T00:00:00Z",
                "2027-01-01T00:00:00Z",
                true,
                listOf("example.org"),
            )
        val tool =
            NetworkTlsTool(
                TlsDiagnosticProbe { host, port, family, timeout ->
                    calls++
                    assertEquals("example.org", host)
                    assertEquals(443, port)
                    assertEquals("auto", family)
                    assertEquals(10, timeout)
                    TlsDiagnosticResult("trusted", "192.0.2.10", "TLSv1.3", "TLS_AES_128_GCM_SHA256", true, true, certificate, 12)
                },
            )

        val invalidHost = tool.execute("""{"host":"https://example.org/path"}""", emptyMap())
        val invalidPort = tool.execute("""{"host":"example.org","port":65536}""", emptyMap())
        val invalidFamily = tool.execute("""{"host":"example.org","family":"any"}""", emptyMap())
        val result = tool.execute("""{"host":"example.org"}""", emptyMap())

        assertFalse(invalidHost.success)
        assertFalse(invalidPort.success)
        assertFalse(invalidFamily.success)
        assertTrue(result.success)
        assertTrue(result.content.contains("\"tlsProtocol\":\"TLSv1.3\""))
        assertTrue(result.content.contains("\"validAtCheckTime\":true"))
        assertEquals(1, calls)
    }

    @Test
    fun `probe selects requested family and retries connection failures within candidate bound`() {
        val first = InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 1))
        val second = InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 2))
        val ipv6 =
            InetAddress.getByAddress(
                ByteArray(16).also {
                    it[0] = 0x20
                    it[1] = 0x01
                    it[2] = 0x0d
                    it[3] = 0xb8.toByte()
                },
            )
        var attempts = 0
        val resolver =
            object : HostResolver {
                override fun resolve(
                    host: String,
                    dnsServer: DnsServerEndpoint?,
                ) = listOf(ipv6, second, first)

                override fun reverse(
                    address: InetAddress,
                    dnsServer: DnsServerEndpoint?,
                ) = emptyList<String>()
            }
        val probe =
            JvmTlsDiagnosticProbe(
                resolver,
                TlsHandshakeProbe { address, serverName, port, timeout ->
                    attempts++
                    assertTrue(address is java.net.Inet4Address)
                    assertEquals("example.org", serverName)
                    assertEquals(443, port)
                    assertTrue(timeout > 0)
                    if (attempts == 1) {
                        TlsHandshakeResult("connection_failed", null, null, null, null, null)
                    } else {
                        TlsHandshakeResult("trusted", "TLSv1.3", "TLS_AES_128_GCM_SHA256", true, true, null)
                    }
                },
            )

        val result = probe.inspect("example.org", 443, "ipv4", 5)

        assertEquals("trusted", result.status)
        assertEquals("192.0.2.2", result.address)
        assertEquals(2, attempts)
        assertTrue(result.trustValidated == true)
    }

    @Test
    fun `probe reports unavailable requested family without starting a handshake`() {
        val resolver =
            object : HostResolver {
                override fun resolve(
                    host: String,
                    dnsServer: DnsServerEndpoint?,
                ) = listOf(InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 1)))

                override fun reverse(
                    address: InetAddress,
                    dnsServer: DnsServerEndpoint?,
                ) = emptyList<String>()
            }
        val probe = JvmTlsDiagnosticProbe(resolver, TlsHandshakeProbe { _, _, _, _ -> error("Unexpected handshake") })

        val result = probe.inspect("example.org", 443, "ipv6", 5)

        assertEquals("family_unavailable", result.status)
        assertNull(result.address)
        assertNull(result.hostnameVerified)
    }

    @Test
    fun `hostname normalization rejects URLs before resolver access`() {
        var resolved = false
        val resolver =
            object : HostResolver {
                override fun resolve(
                    host: String,
                    dnsServer: DnsServerEndpoint?,
                ): List<InetAddress> {
                    resolved = true
                    throw UnknownHostException()
                }

                override fun reverse(
                    address: InetAddress,
                    dnsServer: DnsServerEndpoint?,
                ) = emptyList<String>()
            }
        val tool =
            NetworkTlsTool(
                TlsDiagnosticProbe {
                    host,
                    port,
                    family,
                    timeout,
                    ->
                    JvmTlsDiagnosticProbe(
                        resolver,
                        TlsHandshakeProbe {
                            _,
                            _,
                            _,
                            _,
                            ->
                            error("Unexpected handshake")
                        },
                    ).inspect(host, port, family, timeout)
                },
            )

        val result = tool.execute("""{"host":"example.org/path"}""", emptyMap())

        assertFalse(result.success)
        assertFalse(resolved)
    }
}
