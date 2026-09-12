package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.workspace.ClientDirectoryCapabilityRegistry
import de.heckenmann.visualagent.workspace.VisualAgentProtectedDirectoryPolicy
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies that desktop-owned directory capabilities keep authorization at the client boundary. */
class LocalClientDirectoryGrantAdministrationPortTest {
    @Test
    fun `client capability contains reads writes and revocation within its selected directory`() {
        val temp = createTempDirectory("visual-agent-client-directory-test")
        val root = Files.createDirectory(temp.resolve("documents"))
        Files.writeString(root.resolve("notes.txt"), "before")
        val registry = ClientDirectoryCapabilityRegistry()
        val port =
            LocalClientDirectoryGrantAdministrationPort(
                registry,
                VisualAgentProtectedDirectoryPolicy(":memory:"),
                clientId = "desktop-a",
            )

        val registration = port.prepareDirectoryGrant(root.toString(), DirectoryAccessMode.READ_WRITE)
        val access = registry.requireAccess(registration.grantId, registration.clientId)

        assertEquals("before", access.readText("notes.txt"))
        assertEquals("notes.txt", access.writeText("notes.txt", "after"))
        assertEquals("after", Files.readString(root.resolve("notes.txt")))
        assertFailsWith<IllegalArgumentException> { access.readText("../outside.txt") }

        registry.revokeClient(registration.clientId)

        assertFalse(registry.isAvailable(registration))
        assertFailsWith<IllegalArgumentException> { registry.requireAccess(registration.grantId, registration.clientId) }
    }

    @Test
    fun `client cannot grant data directory or an ancestor of it`() {
        val temp = createTempDirectory("visual-agent-client-directory-test")
        val data = Files.createDirectory(temp.resolve("data"))
        val documents = Files.createDirectory(temp.resolve("documents"))
        val port =
            LocalClientDirectoryGrantAdministrationPort(
                ClientDirectoryCapabilityRegistry(),
                VisualAgentProtectedDirectoryPolicy(data.resolve("visual-agent.db").toString()),
                clientId = "desktop-a",
            )

        assertFailsWith<IllegalArgumentException> {
            port.prepareDirectoryGrant(data.toString(), DirectoryAccessMode.READ_ONLY)
        }
        assertFailsWith<IllegalArgumentException> {
            port.prepareDirectoryGrant(temp.toString(), DirectoryAccessMode.READ_ONLY)
        }

        val registration = port.prepareDirectoryGrant(documents.toString(), DirectoryAccessMode.READ_ONLY)
        assertTrue(registration.grantId.startsWith("grant-"))
    }
}
