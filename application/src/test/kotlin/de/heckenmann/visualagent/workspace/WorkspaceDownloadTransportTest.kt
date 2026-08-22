package de.heckenmann.visualagent.workspace

import io.mockk.mockk
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Verifies protocol and address validation before a remote connection is attempted. */
class WorkspaceDownloadTransportTest {
    private val destination = Files.createTempFile("visual-agent-transport-test", ".part")
    private val control = WorkspaceDownloadControl({}, {})
    private val transport = WorkspaceDownloadTransport(mockk(relaxed = true))

    @Test
    fun `rejects unsupported protocol before transfer`() {
        assertFailsWith<IOException> {
            transport.download(URI("gopher://example.org/file"), destination, control)
        }
    }

    @Test
    fun `rejects private and missing hosts`() {
        assertFailsWith<IllegalArgumentException> {
            transport.download(URI("https:///file"), destination, control)
        }
        assertFailsWith<IllegalArgumentException> {
            transport.download(URI("https://localhost/file"), destination, control)
        }
        assertFailsWith<IllegalArgumentException> {
            transport.download(URI("https://[fd00::1]/file"), destination, control)
        }
    }

    @Test
    fun `scp rejects password credentials before known host lookup`() {
        val scp = WorkspaceScpTransport()
        assertFailsWith<IllegalArgumentException> {
            scp.download(URI("scp://user:secret@example.org/file"), destination, control)
        }
        assertFailsWith<IOException> {
            scp.download(URI("scp://example.org/file"), destination, control)
        }
    }
}
