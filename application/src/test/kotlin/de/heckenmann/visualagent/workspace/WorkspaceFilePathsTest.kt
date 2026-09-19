package de.heckenmann.visualagent.workspace

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals

class WorkspaceFilePathsTest {
    @Test
    fun `workspace root is canonical before resolving nested paths`(
        @TempDir tempDirectory: Path,
    ) {
        val databasePath = tempDirectory.resolve("data/visual-agent.db")
        databasePath.parent.createDirectories()

        val root = WorkspaceFilePaths.workspaceRoot(databasePath.toString())

        assertEquals(root.toRealPath(), root)
        assertEquals(
            root.resolve("reports/result.md"),
            WorkspaceFilePaths.resolveWorkspacePath("reports/result.md", databasePath.toString()),
        )
    }
}
