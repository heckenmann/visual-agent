package de.heckenmann.visualagent.agent.tools

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkRoutesToolTest {
    @Test
    fun `returns bounded route table results for the server`() {
        val tool =
            NetworkRoutesTool(
                RouteTableProbe { family ->
                    assertEquals("all", family)
                    listOf(
                        RouteTableResult(
                            "ipv4",
                            "available",
                            "default via 192.0.2.1",
                            false,
                            listOf(NormalizedRoute("0.0.0.0/0", "192.0.2.1", "eth0", 100, true)),
                        ),
                    )
                },
            )

        val result = tool.execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("visual-agent-server"))
        assertTrue(result.content.contains("192.0.2.1"))
        val data = assertNotNull(result.data).jsonObject
        val tables = data["tables"] as kotlinx.serialization.json.JsonArray
        val routes = tables.single().jsonObject["routes"] as kotlinx.serialization.json.JsonArray
        assertEquals(
            "0.0.0.0/0",
            routes
                .single()
                .jsonObject["destination"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun `rejects model supplied commands or host values`() {
        var calls = 0
        val tool =
            NetworkRoutesTool(
                RouteTableProbe {
                    calls++
                    emptyList()
                },
            )

        val result = tool.execute("{\"command\":\"cat /etc/passwd\"}", emptyMap())

        assertFalse(result.success)
        assertEquals(0, calls)
    }
}
