package de.heckenmann.visualagent.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies that endpoint choices remain explicit and do not silently fall back. */
class DesktopServerEndpointTest {
    @Test
    fun `local endpoint preserves the configured in process name`() {
        assertEquals("visual-agent-local", DesktopServerEndpoint.LocalInProcess("visual-agent-local").name)
    }

    @Test
    fun `remote endpoint preserves host and port`() {
        val endpoint = DesktopServerEndpoint.RemoteTls("server.example", 7443)
        assertEquals("server.example", endpoint.host)
        assertEquals(7443, endpoint.port)
    }

    @Test
    fun `missing remote endpoint selects local transport`() {
        assertEquals(
            DesktopServerEndpoint.LocalInProcess("visual-agent-local"),
            DesktopServerEndpointSelector.select(emptyMap()),
        )
    }

    @Test
    fun `desktop bootstrap resolves the default endpoint`() {
        assertEquals(DesktopServerEndpoint.LocalInProcess("visual-agent-local"), selectEndpoint())
    }

    @Test
    fun `explicit grpcs endpoint selects remote transport`() {
        assertEquals(
            DesktopServerEndpoint.RemoteTls("server.example", 7443),
            DesktopServerEndpointSelector.select(
                mapOf(DesktopServerEndpointSelector.REMOTE_ENDPOINT_PROPERTY to "grpcs://server.example:7443"),
            ),
        )
    }

    @Test
    fun `invalid endpoint scheme is rejected`() {
        val exception =
            runCatching {
                DesktopServerEndpointSelector.select(
                    mapOf(DesktopServerEndpointSelector.REMOTE_ENDPOINT_PROPERTY to "http://server.example:7443"),
                )
            }.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
    }
}
