package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.tools.api.ToolDownloadRequest
import de.heckenmann.visualagent.agent.tools.api.ToolExtractedText
import de.heckenmann.visualagent.agent.tools.api.ToolImageAnalysis
import de.heckenmann.visualagent.agent.tools.api.ToolImageBytes
import de.heckenmann.visualagent.agent.tools.api.ToolImageInfo
import de.heckenmann.visualagent.agent.tools.api.ToolMimeType
import de.heckenmann.visualagent.agent.tools.api.ToolWindowState
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceDirectoryDeletion
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceFile
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceMatch
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceSearch
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceSync
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import de.heckenmann.visualagent.agent.tools.api.WorkspaceLayoutToolPort
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.workspace.WorkspaceDownloadRequest
import de.heckenmann.visualagent.workspace.WorkspaceDownloadService
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService
import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.Base64

/** Application adapter for managed workspace-file operations consumed by tools. */
@Component
class WorkspaceFileToolPortAdapter(
    private val files: WorkspaceFileService,
    private val llmProvider: ObjectProvider<LLMProvider>,
    private val downloads: WorkspaceDownloadService? = null,
) : WorkspaceFileToolPort {
    override fun list(): List<ToolWorkspaceFile> = files.listFiles().map(::toToolFile)

    override fun listDirectories(): List<String> = files.listDirectories()

    override fun createDirectory(
        parentDirectory: String,
        name: String,
    ): String = files.createDirectory(parentDirectory, name)

    override fun search(query: String): ToolWorkspaceSearch =
        files.searchFiles(query).let { result ->
            ToolWorkspaceSearch(result.query, result.matches.map { ToolWorkspaceMatch(it.matchType, it.snippet, toToolFile(it.record)) })
        }

    override fun sync(): ToolWorkspaceSync =
        files.syncMetadataWithFilesystem().let { ToolWorkspaceSync(it.added, it.updated, it.removed, it.total) }

    override fun requireFile(
        id: String?,
        path: String?,
    ): ToolWorkspaceFile = toToolFile(files.requireFile(id, path))

    override fun delete(file: ToolWorkspaceFile): Boolean = files.deleteFile(file.id)

    override fun deleteDirectory(
        relativePath: String,
        recursive: Boolean,
    ): ToolWorkspaceDirectoryDeletion =
        files.deleteDirectory(relativePath, recursive).let {
            ToolWorkspaceDirectoryDeletion(it.relativePath, it.recursive, it.deletedFiles, it.deletedMetadata)
        }

    override fun hash(file: ToolWorkspaceFile): String = files.hash(requireRecord(file))

    override fun readText(file: ToolWorkspaceFile): String = files.readText(requireRecord(file))

    override fun extractPdfText(file: ToolWorkspaceFile): ToolExtractedText =
        files.extractPdfText(requireRecord(file)).let { ToolExtractedText(it.cached, it.text) }

    override fun renderPdfPage(
        file: ToolWorkspaceFile,
        page: Int,
    ): ToolWorkspaceFile = toToolFile(files.renderPdfPage(requireRecord(file), page))

    override fun imageInfo(file: ToolWorkspaceFile): ToolImageInfo =
        files.imageInfo(requireRecord(file)).let { ToolImageInfo(it.mimeType, it.width, it.height, it.sizeBytes, it.sha256) }

    override fun imageBytes(file: ToolWorkspaceFile): ToolImageBytes =
        files.imageBytes(requireRecord(file)).let { ToolImageBytes(it.mimeType, it.base64) }

    override fun analyzeImage(
        file: ToolWorkspaceFile,
        prompt: String,
    ): ToolImageAnalysis {
        val bytes = files.imageBytes(requireRecord(file))
        val response = runBlocking { llmProvider.getObject().vision(Base64.getDecoder().decode(bytes.base64), prompt) }
        return ToolImageAnalysis(response.model, response.message.content)
    }

    override fun detectMimeType(file: ToolWorkspaceFile): ToolMimeType =
        files.detectMimeType(requireRecord(file)).let { ToolMimeType(it.detectedMimeType, it.storedMimeType, it.sizeBytes, it.sha256) }

    override fun download(request: ToolDownloadRequest): ToolWorkspaceFile =
        requireNotNull(downloads) { "Workspace downloads are not configured" }
            .download(WorkspaceDownloadRequest(request.source, request.directory, request.filename))
            .let(::toToolFile)

    private fun requireRecord(file: ToolWorkspaceFile): WorkspaceFileRecord = files.requireFile(file.id, null)
}

/** Application adapter for workspace layout operations consumed by tools. */
@Component
class WorkspaceLayoutToolPortAdapter(
    private val layout: WorkspaceLayoutService,
) : WorkspaceLayoutToolPort {
    override fun reportJson(): String = JSON.encodeToString(layout.report())

    override fun windows(): List<ToolWindowState> = layout.report().windows.map(::toToolWindow)

    override fun apply(windows: List<ToolWindowState>): String =
        JSON.encodeToString(layout.applyWindowStates(windows.map(::toApplicationWindow)))

    private companion object {
        val JSON =
            Json {
                encodeDefaults = true
                prettyPrint = true
            }
    }
}

private fun toToolFile(record: WorkspaceFileRecord) =
    ToolWorkspaceFile(
        record.id,
        record.relativePath,
        record.originalName,
        record.mimeType,
        record.sizeBytes,
        record.sha256,
        record.extractedText != null,
        record.importedAt,
        record.updatedAt,
    )

private fun toToolWindow(window: WorkspaceWindowState) = ToolWindowState(window.id, window.order, window.visible, window.preferredWidth)

private fun toApplicationWindow(window: ToolWindowState) =
    WorkspaceWindowState(window.id, window.order, window.visible, window.preferredWidth)
