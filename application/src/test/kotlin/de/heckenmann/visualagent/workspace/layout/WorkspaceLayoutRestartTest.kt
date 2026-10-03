package de.heckenmann.visualagent.workspace.layout

import de.heckenmann.visualagent.protocol.LayoutPosition
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.server.SpringWorkspaceLayoutPort
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

/** Verifies that protocol layout writes survive closing and reopening file-backed persistence. */
class WorkspaceLayoutRestartTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `latest panel order visibility widths and stage geometry survive a server restart`() {
        val path = directory.resolve("layout").toString()
        val original =
            listOf(
                LayoutWindowState("conversation", 0, true, 720.0),
                LayoutWindowState("todos", 1, true, 480.0),
                LayoutWindowState("files", 2, false, 360.0),
            )
        val reordered = listOf(original[1].copy(order = 0), original[0].copy(order = 1), original[2])
        val stage = LayoutSize(1440.0, 900.0)
        val position = LayoutPosition(120.0, 80.0)
        KnowledgeDbTestFactory.create(path).use { persistence ->
            val port = SpringWorkspaceLayoutPort(WorkspaceLayoutService(WorkspaceLayoutPersistence(persistence.preferenceStore)))
            port.bind(stage, LayoutSize(1400.0, 820.0), original)
            port.applyWindowStates(original, false)
            port.applyWindowStates(reordered, false)
            port.saveStage(stage, position)
            assertEquals(reordered, port.report().windows)
        }
        KnowledgeDbTestFactory.create(path).use { persistence ->
            val port = SpringWorkspaceLayoutPort(WorkspaceLayoutService(WorkspaceLayoutPersistence(persistence.preferenceStore)))
            val restored = port.report()
            assertEquals(reordered, restored.windows)
            assertEquals(stage, restored.stage)
            assertEquals(position, restored.stagePosition)
        }
    }
}
