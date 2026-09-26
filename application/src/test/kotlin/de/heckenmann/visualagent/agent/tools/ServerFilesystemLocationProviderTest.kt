package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerFilesystemLocationProviderTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `selects server-owned paths without creating missing directories`() {
        val database = tempDirectory.resolve("visual-agent.db")
        val workspace = tempDirectory.resolve("workspace")
        val locations =
            ServerFilesystemLocationProvider(tempDirectory, database.toString())
                .locations()
                .associateBy { it.id }

        assertEquals(tempDirectory, locations.getValue("server-data").path)
        assertEquals(workspace, locations.getValue("workspace").path)
        assertEquals(tempDirectory, locations.getValue("database").path)
        assertTrue("temporary" in locations)
        assertFalse(Files.exists(workspace))
    }

    @Test
    fun `does not report a filesystem directory for an in-memory database`() {
        val locations =
            ServerFilesystemLocationProvider(tempDirectory, "jdbc:h2:mem:visual-agent")
                .locations()

        assertFalse(locations.any { it.id == "database" })
    }
}
