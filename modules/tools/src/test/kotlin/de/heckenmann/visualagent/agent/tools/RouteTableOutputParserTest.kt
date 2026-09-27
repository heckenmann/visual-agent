package de.heckenmann.visualagent.agent.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteTableOutputParserTest {
    @Test
    fun `parses Linux IPv4 and IPv6 defaults and metrics`() {
        val ipv4 =
            RouteTableOutputParser.parse(
                RouteTablePlatform.LINUX,
                "ipv4",
                "default via 192.0.2.1 dev eth0 proto dhcp metric 100\n192.0.2.0/24 dev eth0 scope link metric 10",
            )
        val ipv6 =
            RouteTableOutputParser.parse(
                RouteTablePlatform.LINUX,
                "ipv6",
                "default via fe80::1 dev enp0s1 proto ra metric 2048",
            )

        assertEquals("0.0.0.0/0", ipv4.first().destination)
        assertEquals("192.0.2.1", ipv4.first().gateway)
        assertEquals("eth0", ipv4.first().networkInterface)
        assertEquals(100L, ipv4.first().metric)
        assertTrue(ipv4.first().defaultRoute)
        assertEquals("192.0.2.0/24", ipv4[1].destination)
        assertFalse(ipv4[1].defaultRoute)
        assertEquals("::/0", ipv6.single().destination)
        assertEquals(2048L, ipv6.single().metric)
    }

    @Test
    fun `parses macOS routing rows and leaves unavailable metrics empty`() {
        val routes =
            RouteTableOutputParser.parse(
                RouteTablePlatform.MACOS,
                "ipv4",
                """
                Routing tables
                Destination        Gateway            Flags        Netif Expire
                default            192.0.2.1          UGScg        en0
                192.0.2            link#14            UCS          en0
                """.trimIndent(),
            )

        assertEquals("0.0.0.0/0", routes.first().destination)
        assertEquals("192.0.2.1", routes.first().gateway)
        assertEquals("en0", routes.first().networkInterface)
        assertEquals(null, routes.first().metric)
        assertTrue(routes.first().defaultRoute)
        assertEquals(2, routes.size)
    }

    @Test
    fun `parses Windows IPv4 and IPv6 active routes`() {
        val ipv4 =
            RouteTableOutputParser.parse(
                RouteTablePlatform.WINDOWS,
                "ipv4",
                """
                Active Routes:
                Network Destination        Netmask          Gateway       Interface  Metric
                0.0.0.0                    0.0.0.0          192.0.2.1     192.0.2.10  25
                """.trimIndent(),
            )
        val ipv6 =
            RouteTableOutputParser.parse(
                RouteTablePlatform.WINDOWS,
                "ipv6",
                """
                Active Routes:
                If Metric Network Destination Gateway
                12 5 ::/0 fe80::1
                """.trimIndent(),
            )

        assertEquals("0.0.0.0/0", ipv4.single().destination)
        assertEquals("192.0.2.1", ipv4.single().gateway)
        assertEquals("192.0.2.10", ipv4.single().networkInterface)
        assertEquals(25L, ipv4.single().metric)
        assertTrue(ipv4.single().defaultRoute)
        assertEquals("::/0", ipv6.single().destination)
        assertEquals("fe80::1", ipv6.single().gateway)
        assertEquals("12", ipv6.single().networkInterface)
        assertEquals(5L, ipv6.single().metric)
    }

    @Test
    fun `parses Windows routes without relying on English section headings`() {
        val ipv4 =
            RouteTableOutputParser.parse(
                RouteTablePlatform.WINDOWS,
                "ipv4",
                """
                Aktive Routen:
                Netzwerkziel        Netzwerkmaske          Gateway       Schnittstelle  Metrik
                0.0.0.0             0.0.0.0                 192.0.2.1     192.0.2.10     25
                """.trimIndent(),
            )
        val ipv6 =
            RouteTableOutputParser.parse(
                RouteTablePlatform.WINDOWS,
                "ipv6",
                """
                Aktive Routen:
                Wenn Metrik Netzwerkziel Gateway
                12 5 ::/0 fe80::1
                """.trimIndent(),
            )

        assertEquals("0.0.0.0/0", ipv4.single().destination)
        assertEquals("::/0", ipv6.single().destination)
    }
}
