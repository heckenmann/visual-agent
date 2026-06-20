package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.ui.panels.canvas.CanvasPanel
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.control.TextInputDialog
import javafx.scene.control.Tooltip
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Workspace file management panel for imported user files.
 */
@Component
@Lazy
class FilesPanel(
    private val workspaceFiles: WorkspaceFileService,
    private val canvasPanel: CanvasPanel,
) : Region() {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private val table = TableView<WorkspaceFileRecord>()
    private val statusLabel = Label("No files loaded")
    private val searchField = TextField()
    private var onFilesImported: ((List<WorkspaceFileRecord>) -> Unit)? = null
    private var onCanvasOpened: (() -> Unit)? = null

    init {
        styleClass.add("files-panel")
        children.add(contentLayout())
        canvasPanel.setWorkspaceFileActions(onSave = { saveCanvasToWorkspace() }, onOpen = { openSelectedInCanvas() })
        refresh()
    }

    /** Registers a callback invoked after files are imported through this panel. */
    fun setOnFilesImported(callback: (List<WorkspaceFileRecord>) -> Unit) {
        onFilesImported = callback
    }

    /** Registers a callback invoked after an image is inserted into the canvas. */
    fun setOnCanvasOpened(callback: () -> Unit) {
        onCanvasOpened = callback
    }

    /** Opens a file chooser and imports the selected files. */
    fun importWithDialog(): List<WorkspaceFileRecord> {
        val files =
            FileChooser()
                .apply {
                    title = "Import files into workspace"
                    extensionFilters.addAll(
                        FileChooser.ExtensionFilter(
                            "Supported files",
                            "*.png",
                            "*.jpg",
                            "*.jpeg",
                            "*.gif",
                            "*.bmp",
                            "*.pdf",
                            "*.txt",
                            "*.md",
                            "*.csv",
                            "*.json",
                            "*.xml",
                            "*.log",
                        ),
                        FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                        FileChooser.ExtensionFilter("PDF documents", "*.pdf"),
                        FileChooser.ExtensionFilter("Text files", "*.txt", "*.md", "*.csv", "*.json", "*.xml", "*.log"),
                        FileChooser.ExtensionFilter("All files", "*.*"),
                    )
                }.showOpenMultipleDialog(scene?.window)
                ?: return emptyList()
        val imported = files.map(workspaceFiles::importFile)
        refresh()
        onFilesImported?.invoke(imported)
        return imported
    }

    /** Refreshes the file list from persisted metadata. */
    fun refresh() {
        val query = searchField.text.orEmpty().trim()
        if (query.isBlank()) {
            table.items.setAll(workspaceFiles.listFiles())
            statusLabel.text = "${table.items.size} workspace files"
        } else {
            search(query)
        }
    }

    /** Searches managed workspace files and displays matching records. */
    fun search(query: String) {
        val result = workspaceFiles.searchFiles(query)
        table.items.setAll(result.matches.map { it.record })
        statusLabel.text = "${result.matches.size} matches for \"$query\""
    }

    /** Reconciles workspace metadata with files on disk and refreshes the panel. */
    fun syncWorkspace() {
        val report = workspaceFiles.syncMetadataWithFilesystem()
        searchField.clear()
        table.items.setAll(workspaceFiles.listFiles())
        statusLabel.text = "Sync complete: ${report.added} added, ${report.updated} updated, ${report.removed} removed"
    }

    internal fun deleteFile(record: WorkspaceFileRecord) {
        workspaceFiles.deleteFile(record.id)
        refresh()
    }

    internal fun renameFile(
        record: WorkspaceFileRecord,
        newName: String,
    ) {
        if (newName.isBlank()) return
        workspaceFiles.renameFile(record.id, newName)
        refresh()
    }

    internal fun openFileInCanvas(record: WorkspaceFileRecord) {
        val file = workspaceFiles.resolveManagedPath(record.relativePath).toFile()
        when {
            record.mimeType.startsWith("image/") -> canvasPanel.addWorkspaceImage(file)
            isCanvasDocument(record) -> canvasPanel.openCanvasDocument(file)
            else -> return
        }
        onCanvasOpened?.invoke()
    }

    internal fun copyPath(record: WorkspaceFileRecord) {
        copyText(record.relativePath)
    }

    internal fun copyHash(record: WorkspaceFileRecord) {
        copyText(record.sha256)
    }

    override fun layoutChildren() {
        children.singleOrNull()?.resizeRelocate(0.0, 0.0, width, height)
    }

    private fun contentLayout(): VBox {
        table.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        table.columns.setAll(nameColumn(), typeColumn(), sizeColumn(), hashColumn(), importedColumn())
        VBox.setVgrow(table, Priority.ALWAYS)
        return VBox(header(), toolbar(), table, statusLabel).apply {
            spacing = 10.0
            padding = Insets(14.0)
            styleClass.add("files-root")
        }
    }

    private fun header(): VBox =
        VBox(
            Label("Workspace Files").apply { styleClass.add("panel-title") },
            Label("Import, inspect, rename, delete, and send files to agent tools.").apply { styleClass.add("field-help") },
        ).apply { spacing = 3.0 }

    private fun toolbar(): HBox =
        HBox(
            searchField.apply {
                promptText = "Search workspace"
                setOnAction { searchFromField() }
                HBox.setHgrow(this, Priority.ALWAYS)
            },
            actionButton("Search", FontAwesomeSolid.SEARCH) { searchFromField() },
            actionButton("Import", FontAwesomeSolid.FOLDER_OPEN) { importWithDialog() },
            actionButton("Delete", FontAwesomeSolid.TRASH) { deleteSelected() },
            actionButton("Rename", FontAwesomeSolid.I_CURSOR) { renameSelected() },
            actionButton("Refresh", FontAwesomeSolid.SYNC) { refresh() },
            actionButton("Sync DB", FontAwesomeSolid.DATABASE) { syncWorkspace() },
            actionButton("Save Canvas", FontAwesomeSolid.SAVE) { saveCanvasToWorkspace() },
            actionButton("Open in Canvas", FontAwesomeSolid.IMAGE) { openSelectedInCanvas() },
            actionButton("Copy Path", FontAwesomeSolid.COPY) { copySelectedPath() },
            actionButton("Copy Hash", FontAwesomeSolid.FINGERPRINT) { copySelectedHash() },
        ).apply {
            spacing = 8.0
            styleClass.add("files-toolbar")
        }

    private fun actionButton(
        text: String,
        icon: FontAwesomeSolid,
        action: () -> Unit,
    ): Button =
        Button(null, FontIcon(icon)).apply {
            styleClass.add("chat-toolbar-button")
            isFocusTraversable = false
            tooltip = Tooltip(text)
            setOnAction { action() }
        }

    private fun nameColumn(): TableColumn<WorkspaceFileRecord, String> =
        TableColumn<WorkspaceFileRecord, String>("Name").apply {
            setCellValueFactory { SimpleStringProperty(it.value.relativePath) }
        }

    private fun typeColumn(): TableColumn<WorkspaceFileRecord, String> =
        TableColumn<WorkspaceFileRecord, String>("Type").apply {
            setCellValueFactory { SimpleStringProperty(it.value.mimeType) }
        }

    private fun sizeColumn(): TableColumn<WorkspaceFileRecord, String> =
        TableColumn<WorkspaceFileRecord, String>("Size").apply {
            setCellValueFactory { SimpleStringProperty(formatBytes(it.value.sizeBytes)) }
        }

    private fun hashColumn(): TableColumn<WorkspaceFileRecord, String> =
        TableColumn<WorkspaceFileRecord, String>("SHA-256").apply {
            setCellValueFactory { SimpleStringProperty(it.value.sha256.take(12)) }
        }

    private fun importedColumn(): TableColumn<WorkspaceFileRecord, String> =
        TableColumn<WorkspaceFileRecord, String>("Imported").apply {
            setCellValueFactory { SimpleStringProperty(timeFormatter.format(it.value.importedAt)) }
        }

    private fun deleteSelected() {
        val selected = selectedFile() ?: return
        val answer =
            Alert(Alert.AlertType.CONFIRMATION, "Delete ${selected.relativePath}?")
                .apply {
                    title = "Delete workspace file"
                    headerText = "Delete imported file"
                }.showAndWait()
        if (answer.isPresent && answer.get().buttonData.isDefaultButton) {
            deleteFile(selected)
        }
    }

    private fun renameSelected() {
        val selected = selectedFile() ?: return
        val result =
            TextInputDialog(File(selected.relativePath).name)
                .apply {
                    title = "Rename workspace file"
                    headerText = "Rename ${selected.relativePath}"
                    contentText = "New filename"
                }.showAndWait()
        if (result.isPresent) renameFile(selected, result.get())
    }

    private fun openSelectedInCanvas() {
        openFileInCanvas(selectedFile() ?: return)
    }

    private fun saveCanvasToWorkspace() {
        val record =
            workspaceFiles.createManagedFile(
                directoryName = "canvas",
                requestedName = "canvas-${Instant.now().toEpochMilli()}.draw",
                bytes = canvasPanel.canvasDocumentBytes(),
                mimeType = CANVAS_MIME_TYPE,
            )
        searchField.clear()
        refresh()
        table.selectionModel.select(record)
        onFilesImported?.invoke(listOf(record))
    }

    private fun searchFromField() {
        val query = searchField.text.orEmpty().trim()
        if (query.isBlank()) refresh() else search(query)
    }

    private fun copySelectedPath() {
        selectedFile()?.let(::copyPath)
    }

    private fun copySelectedHash() {
        selectedFile()?.let(::copyHash)
    }

    private fun selectedFile(): WorkspaceFileRecord? = table.selectionModel.selectedItem

    private fun isCanvasDocument(record: WorkspaceFileRecord): Boolean =
        record.mimeType == CANVAS_MIME_TYPE ||
            record.relativePath.endsWith(".draw", ignoreCase = true) ||
            record.relativePath.endsWith(".canvas", ignoreCase = true) ||
            record.relativePath.endsWith(".jhd", ignoreCase = true)

    private fun copyText(text: String) {
        Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(text) })
    }

    private fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1024L * 1024L -> "${bytes / 1024L / 1024L} MB"
            bytes >= 1024L -> "${bytes / 1024L} KB"
            else -> "$bytes B"
        }

    private companion object {
        const val CANVAS_MIME_TYPE = "application/vnd.visual-agent.canvas+xml"
    }
}
