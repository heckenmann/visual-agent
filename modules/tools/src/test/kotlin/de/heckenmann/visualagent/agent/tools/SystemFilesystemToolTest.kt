package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemFilesystemToolTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `tool returns server-scoped storage metadata without exposing paths`() {
        val missingWorkspace = tempDirectory.resolve("not-created").resolve("workspace")
        val provider =
            JvmFilesystemSnapshotProvider(
                FilesystemLocationProvider {
                    listOf(
                        FilesystemLocation("server-data", tempDirectory),
                        FilesystemLocation("workspace", missingWorkspace),
                    )
                },
            )
        val tool = SystemFilesystemTool(provider)

        val result = tool.execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("\"hostScope\":\"visual-agent-server\""))
        assertTrue(result.content.contains("\"id\":\"server-data\""))
        assertTrue(result.content.contains("\"id\":\"workspace\""))
        assertTrue(result.content.contains("\"exists\":false"))
        assertTrue(result.content.contains("\"totalBytes\""))
        assertFalse(result.content.contains(tempDirectory.toString()))
    }

    @Test
    fun `tool reports normalized failure when location provider fails`() {
        val tool = SystemFilesystemTool(FilesystemSnapshotProvider { error("private path detail") })

        val result = tool.execute("{}", emptyMap())

        assertFalse(result.success)
        assertFalse(result.content.contains("private path detail"))
        assertEquals("Server storage diagnostics are unavailable.", result.error)
    }
}
