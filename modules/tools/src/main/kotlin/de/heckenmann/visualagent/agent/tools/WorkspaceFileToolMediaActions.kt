package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.DirectoryToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryGrant
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceFile
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Keeps image-specific workspace-file tool serialization independent from command dispatch. */
internal class WorkspaceFileToolMediaActions(
    private val workspaceFiles: WorkspaceFileToolPort,
) {
    fun workspaceEntries(path: String) =
        buildJsonObject {
            val directory = path.trim('/')
            put(
                "entries",
                buildJsonArray {
                    workspaceFiles
                        .listDirectories()
                        .filter { it.substringBeforeLast('/', "") == directory }
                        .forEach { child ->
                            add(
                                buildJsonObject {
                                    put("path", child)
                                    put("directory", true)
                                },
                            )
                        }
                    workspaceFiles
                        .list()
                        .filter { it.relativePath.substringBeforeLast('/', "") == directory }
                        .forEach { child ->
                            add(
                                buildJsonObject {
                                    put("path", child.relativePath)
                                    put("directory", false)
                                    put("sizeBytes", child.sizeBytes)
                                },
                            )
                        }
                },
            )
        }

    fun imageInfo(record: ToolWorkspaceFile) =
        workspaceFileJson(record).let { json ->
            val info = workspaceFiles.imageInfo(record)
            buildJsonObject {
                json.forEach { (key, value) -> put(key, value) }
                put("mimeType", info.mimeType)
                put("width", info.width)
                put("height", info.height)
                put("sizeBytes", info.sizeBytes)
                put("sha256", info.sha256)
            }
        }

    fun imageBytes(record: ToolWorkspaceFile) =
        workspaceFiles.imageBytes(record).let { bytes ->
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("mimeType", bytes.mimeType)
                put("base64", bytes.base64)
            }
        }

    fun analyzeImage(
        record: ToolWorkspaceFile,
        prompt: String,
    ) = workspaceFiles.analyzeImage(record, prompt).let { response ->
        buildJsonObject {
            put("id", record.id)
            put("path", record.relativePath)
            put("model", response.model)
            put("content", response.content)
        }
    }
}

/** Returns a safe metadata representation of a managed workspace file. */
internal fun workspaceFileJson(record: ToolWorkspaceFile) =
    buildJsonObject {
        put("id", record.id)
        put("path", record.relativePath)
        put("originalName", record.originalName)
        put("mimeType", record.mimeType)
        put("sizeBytes", record.sizeBytes)
        put("sha256", record.sha256)
        put("importedAt", record.importedAt.toString())
        put("updatedAt", record.updatedAt.toString())
        put("hasExtractedText", record.hasExtractedText)
    }

/** Fails closed when a direct test does not configure granted-directory access. */
internal object UnsupportedDirectoryToolPort : DirectoryToolPort {
    override fun listGrants(): List<ToolDirectoryGrant> = emptyList()

    override fun list(
        grantId: String,
        path: String,
    ) = error("Granted directory access is not configured")

    override fun readText(
        grantId: String,
        path: String,
    ) = error("Granted directory access is not configured")

    override fun detectMimeType(
        grantId: String,
        path: String,
    ) = error("Granted directory access is not configured")

    override fun search(
        grantId: String,
        query: String,
        path: String,
    ) = error("Granted directory access is not configured")

    override fun glob(
        grantId: String,
        path: String,
        pattern: String,
    ) = error("Granted directory access is not configured")

    override fun writeText(
        grantId: String,
        path: String,
        content: String,
    ) = error("Granted directory access is not configured")

    override fun editText(
        grantId: String,
        path: String,
        oldText: String,
        newText: String,
    ) = error("Granted directory access is not configured")

    override fun createDirectory(
        grantId: String,
        path: String,
    ) = error("Granted directory access is not configured")

    override fun delete(
        grantId: String,
        path: String,
        recursive: Boolean,
    ) = error("Granted directory access is not configured")

    override fun copy(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ) = error("Granted directory access is not configured")

    override fun move(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ) = error("Granted directory access is not configured")
}
