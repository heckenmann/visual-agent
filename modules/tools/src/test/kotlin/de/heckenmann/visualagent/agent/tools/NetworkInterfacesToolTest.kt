package de.heckenmann.visualagent.agent.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkInterfacesToolTest {
    @Test
    fun `tool sorts and returns bounded interface details without hardware addresses`() {
        val provider =
            NetworkInterfaceSnapshotProvider {
                listOf(
                    InterfaceSnapshot("zeta", true, false, false, true, 1500, listOf("192.0.2.9", "192.0.2.2")),
                    InterfaceSnapshot("alpha", true, false, false, true, 9000, listOf("2001:db8::1")),
                )
            }

        val result = NetworkInterfacesTool(provider).execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.indexOf("alpha") < result.content.indexOf("zeta"))
        assertTrue(result.content.contains("2001:db8::1"))
        assertTrue(result.content.contains("192.0.2.2"))
        assertFalse(result.content.contains("hardwareAddress"))
    }

    @Test
    fun `tool caps interface count and address count`() {
        val provider =
            NetworkInterfaceSnapshotProvider {
                (1..65).map { index ->
                    InterfaceSnapshot(
                        name = "if${index.toString().padStart(2, '0')}",
                        up = true,
                        loopback = false,
                        pointToPoint = false,
                        multicast = true,
                        mtu = 1500,
                        addresses = (1..5).map { address -> "192.0.$index.$address" },
                    )
                }
            }

        val result = NetworkInterfacesTool(provider).execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("\"truncated\":true"))
        assertFalse(result.content.contains("if65"))
        assertEquals(256, Regex("192\\.0\\.").findAll(result.content).count())
    }

    @Test
    fun `tool normalizes unavailable interface snapshot`() {
        val tool = NetworkInterfacesTool(NetworkInterfaceSnapshotProvider { error("private host detail") })

        val result = tool.execute("{}", emptyMap())

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("unavailable"))
        assertFalse(result.error.orEmpty().contains("private host detail"))
    }
}
