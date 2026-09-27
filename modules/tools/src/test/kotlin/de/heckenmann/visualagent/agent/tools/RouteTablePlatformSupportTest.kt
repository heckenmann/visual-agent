package de.heckenmann.visualagent.agent.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RouteTablePlatformSupportTest {
    @Test
    fun `builds fixed commands for IPv4 and IPv6 on each supported platform`() {
        assertEquals(
            listOf("ip", "-4", "route", "show", "table", "main"),
            RouteTableCommandBuilder.commands(RouteTablePlatform.LINUX, "ipv4").single().second,
        )
        assertEquals(
            listOf("ip", "-6", "route", "show", "table", "main"),
            RouteTableCommandBuilder.commands(RouteTablePlatform.LINUX, "ipv6").single().second,
        )
        assertEquals(
            listOf("netstat", "-rn", "-f", "inet"),
            RouteTableCommandBuilder.commands(RouteTablePlatform.MACOS, "ipv4").single().second,
        )
        assertEquals(
            listOf("netstat", "-rn", "-f", "inet6"),
            RouteTableCommandBuilder.commands(RouteTablePlatform.MACOS, "ipv6").single().second,
        )
        assertEquals(listOf("route", "print", "-4"), RouteTableCommandBuilder.commands(RouteTablePlatform.WINDOWS, "ipv4").single().second)
        assertEquals(listOf("route", "print", "-6"), RouteTableCommandBuilder.commands(RouteTablePlatform.WINDOWS, "ipv6").single().second)
    }

    @Test
    fun `all requests both families and invalid families are rejected`() {
        assertEquals(2, RouteTableCommandBuilder.commands(RouteTablePlatform.LINUX, "all").size)
        assertFailsWith<IllegalArgumentException> { RouteTableCommandBuilder.commands(RouteTablePlatform.LINUX, "arbitrary") }
    }

    @Test
    fun `recognizes only supported server operating systems`() {
        assertEquals(RouteTablePlatform.LINUX, RouteTablePlatform.fromOsName("Linux"))
        assertEquals(RouteTablePlatform.MACOS, RouteTablePlatform.fromOsName("Mac OS X"))
        assertEquals(RouteTablePlatform.WINDOWS, RouteTablePlatform.fromOsName("Windows 11"))
        assertNull(RouteTablePlatform.fromOsName("Plan 9"))
    }
}
