package de.heckenmann.visualagent.agent.tools

import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkHttpToolTest {
    @Test
    fun `URL validation accepts HTTP(S) and rejects credentials fragments invalid ports and other schemes`() {
        assertEquals("https", parseHttpDiagnosticUri("https://example.org/health?probe=1").scheme)
        assertEquals("http", parseHttpDiagnosticUri("http://[2001:db8::1]:8080/").scheme)

        listOf(
            "ftp://example.org",
            "https://user:secret@example.org",
            "https://example.org/#token",
            "https://example.org:0",
            "https://example.org:70000",
        ).forEach { value ->
            assertFalse(runCatching { parseHttpDiagnosticUri(value) }.isSuccess, value)
        }
    }

    @Test
    fun `tool validates method and timeout and returns no response body`() {
        var calls = 0
        val tool =
            NetworkHttpTool(
                HttpDiagnosticProbe { _, method, _ ->
                    calls++
                    assertEquals("HEAD", method)
                    HttpDiagnosticResult("completed", 204, "text/plain", 0, emptyList(), 7)
                },
            )

        val invalidMethod = tool.execute("""{"url":"https://example.org","method":"POST"}""", emptyMap())
        val invalidTimeout = tool.execute("""{"url":"https://example.org","timeoutSeconds":31}""", emptyMap())
        val result = tool.execute("""{"url":"https://example.org","method":"head"}""", emptyMap())

        assertFalse(invalidMethod.success)
        assertFalse(invalidTimeout.success)
        assertTrue(result.success)
        assertTrue(result.content.contains("\"responseBodyExposed\":false"))
        assertEquals(1, calls)
    }

    @Test
    fun `probe follows safe redirects and returns only sanitized authorities`() {
        val requests = mutableListOf<String>()
        val transport =
            HttpDiagnosticTransport { uri, method, timeout ->
                assertEquals("GET", method)
                assertTrue(timeout > 0)
                requests += uri.toString()
                if (requests.size == 1) {
                    HttpDiagnosticResponse(302, null, null, "https://status.example/ok?access=secret")
                } else {
                    HttpDiagnosticResponse(200, "application/json; charset=utf-8", 12, null)
                }
            }

        val result = JvmHttpDiagnosticProbe(transport).inspect(URI("https://example.org/check?token=secret"), "GET", 5)

        assertEquals("completed", result.status)
        assertEquals(200, result.httpStatus)
        assertEquals("application/json", result.contentType)
        assertEquals(12, result.contentLength)
        assertEquals(1, result.redirects.size)
        assertEquals("https://example.org", result.redirects.single().from)
        assertEquals("https://status.example", result.redirects.single().to)
        assertFalse(result.redirects.toString().contains("secret"))
        assertEquals(2, requests.size)
    }

    @Test
    fun `probe rejects HTTPS downgrade and caps redirect count`() {
        var calls = 0
        val downgradeTransport =
            HttpDiagnosticTransport { _, _, _ ->
                HttpDiagnosticResponse(302, null, null, "http://example.org/unsafe")
            }
        val downgrade = JvmHttpDiagnosticProbe(downgradeTransport).inspect(URI("https://example.org"), "GET", 5)
        val loopingTransport =
            HttpDiagnosticTransport { _, _, _ ->
                calls++
                HttpDiagnosticResponse(302, null, null, "/next")
            }
        val loop = JvmHttpDiagnosticProbe(loopingTransport).inspect(URI("https://example.org"), "GET", 5)

        assertEquals("redirect_rejected", downgrade.status)
        assertEquals("redirect_limit", loop.status)
        assertEquals(6, calls)
        assertEquals(5, loop.redirects.size)
    }

    @Test
    fun `probe normalizes transport errors without returning exception details`() {
        val probe = JvmHttpDiagnosticProbe(HttpDiagnosticTransport { _, _, _ -> throw IOException("private response detail") })

        val result = probe.inspect(URI("https://example.org"), "GET", 5)

        assertEquals("request_failed", result.status)
        assertFalse(result.toString().contains("private response detail"))
    }
}
