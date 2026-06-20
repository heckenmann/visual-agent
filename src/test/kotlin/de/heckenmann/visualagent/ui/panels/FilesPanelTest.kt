package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import de.heckenmann.visualagent.testsupport.TestPng
import de.heckenmann.visualagent.ui.panels.canvas.CanvasPanel
import de.heckenmann.visualagent.ui.panels.canvas.InMemoryPreferenceStore
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.input.Clipboard
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilesPanelTest {
    @Test
    fun `files panel refreshes copies metadata and opens images in canvas`() =
        FxTestSupport.run {
            val previous = AppConfig.instance.databasePath
            try {
                AppConfig.instance.databasePath =
                    Files
                        .createTempDirectory("visual-agent-files-panel")
                        .resolve("data/visual-agent.db")
                        .toString()
                val service = WorkspaceFileService(FakeWorkspaceFileStore())
                val textPath = Files.createTempFile("panel-notes", ".txt")
                Files.writeString(textPath, "Alpha")
                val textFile = service.importFile(textPath.toFile())
                val imagePath = Files.createTempFile("panel-image", ".png")
                TestPng.write(imagePath, 1, 1)
                val imported = service.importFile(imagePath.toFile())
                var openedCanvas = false
                var importedCallbackCount = 0
                val panel = FilesPanel(service, CanvasPanel(InMemoryPreferenceStore()))
                panel.setOnFilesImported { importedCallbackCount += it.size }
                panel.setOnCanvasOpened { openedCanvas = true }

                panel.refresh()
                val table = descendants(panel).filterIsInstance<TableView<*>>().single()
                val searchField = descendants(panel).filterIsInstance<TextField>().single()
                table.selectionModel.select(table.items.indexOf(imported))
                buttonByTooltip(panel, "Copy Path").fire()
                assertEquals(imported.relativePath, Clipboard.getSystemClipboard().string)
                buttonByTooltip(panel, "Copy Hash").fire()
                assertEquals(imported.sha256, Clipboard.getSystemClipboard().string)
                searchField.text = "Alpha"
                buttonByTooltip(panel, "Search").fire()
                assertEquals(1, table.items.size)
                val unmanaged = service.workspaceRoot().resolve("imports/manual.txt")
                unmanaged.parent.toFile().mkdirs()
                Files.writeString(unmanaged, "Manual")
                buttonByTooltip(panel, "Sync DB").fire()
                assertTrue(table.items.any { (it as WorkspaceFileRecord).relativePath == "imports/manual.txt" })
                buttonByTooltip(panel, "Save Canvas").fire()
                val canvasRecord = table.items.filterIsInstance<WorkspaceFileRecord>().first { it.relativePath.startsWith("canvas/") }
                table.selectionModel.select(table.items.indexOf(canvasRecord))
                buttonByTooltip(panel, "Open in Canvas").fire()
                buttonByTooltip(panel, "Refresh").fire()
                buttonByTooltip(panel, "Open in Canvas").fire()
                panel.renameFile(textFile, "renamed")
                val renamed = service.requireFile(null, "imports/renamed.txt")
                panel.deleteFile(renamed)
                panel.resize(500.0, 320.0)
                panel.layout()

                assertTrue(openedCanvas)
                assertTrue(importedCallbackCount >= 1)
                assertTrue(table.items.isNotEmpty())
            } finally {
                AppConfig.instance.databasePath = previous
            }
        }

    private fun descendants(root: Parent): List<Node> =
        root.childrenUnmodifiable.flatMap { child ->
            listOf(child) + if (child is Parent) descendants(child) else emptyList()
        }

    private fun buttonByTooltip(
        root: Parent,
        tooltip: String,
    ): Button = descendants(root).filterIsInstance<Button>().first { it.tooltip?.text == tooltip }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }

    private class FakeWorkspaceFileStore : WorkspaceFileStore {
        private val records = linkedMapOf<String, WorkspaceFileRecord>()

        override fun saveWorkspaceFile(record: WorkspaceFileRecord) {
            records[record.id] = record
        }

        override fun listWorkspaceFiles(): List<WorkspaceFileRecord> = records.values.toList()

        override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = records[id]

        override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? =
            records.values.firstOrNull { it.relativePath == relativePath }

        override fun deleteWorkspaceFile(id: String): Boolean = records.remove(id) != null
    }
}
