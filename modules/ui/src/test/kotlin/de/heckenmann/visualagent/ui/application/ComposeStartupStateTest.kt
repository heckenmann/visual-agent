package de.heckenmann.visualagent.ui.application

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeStartupStateTest {
    @Test
    fun `initial status tells the user that the ui is starting`() {
        val status = StartupStatus.initial()

        assertEquals(StartupPhase.STARTING_UI, status.phase)
        assertEquals("Starting the user interface", status.message())
    }

    @Test
    fun `server and runtime phases expose safe messages`() {
        assertEquals("Resolving server endpoint", StartupStatus.resolvingEndpoint().message())
        assertEquals("Starting the local server", StartupStatus.startingServer().message())
        assertEquals("Connecting to remote server", StartupStatus.connectingRemote().message())
        assertEquals("Loading Visual Agent", StartupStatus.loadingRuntime().message())
        assertEquals("Connecting to Visual Agent", StartupStatus.handshaking().message())
        assertEquals("Ready", StartupStatus.ready().message())
    }

    @Test
    fun `failure without details uses a generic message`() {
        val status = StartupStatus.failed()

        assertEquals(StartupPhase.FAILED, status.phase)
        assertEquals("The server could not be started", status.message())
    }

    @Test
    fun `failure can show a sanitized detail`() {
        val status = StartupStatus(StartupPhase.FAILED, "Remote server is unavailable")

        assertEquals("Remote server is unavailable", status.message())
    }
}
