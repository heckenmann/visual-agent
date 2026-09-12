package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceDeleteResult
import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceReadLimitExceededException
import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceReadResult
import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceWriteResult
import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceWriter
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

/** Delegates JavaScript workspace operations to the managed server-side file API. */
@Service
class WorkspaceJavaScriptWriter(
    private val workspaceFiles: WorkspaceFileService,
) : JavaScriptWorkspaceWriter {
    /** Writes a generated text file through the managed workspace boundary. */
    override fun write(
        relativePath: String,
        content: String,
    ): JavaScriptWorkspaceWriteResult =
        workspaceFiles.writeText(relativePath, content).let { record ->
            JavaScriptWorkspaceWriteResult(record.relativePath, record.sizeBytes, record.mimeType)
        }

    /** Reads a bounded registered workspace file through the managed workspace boundary. */
    override fun read(
        relativePath: String,
        maxBytes: Long,
    ): JavaScriptWorkspaceReadResult {
        require(maxBytes > 0) { "Workspace read limit must be positive" }
        val record = workspaceFiles.requireFile(null, relativePath)
        val bytes =
            try {
                workspaceFiles.readBytes(record, maxBytes)
            } catch (_: IllegalArgumentException) {
                throw JavaScriptWorkspaceReadLimitExceededException()
            }
        return JavaScriptWorkspaceReadResult(record.relativePath, bytes.toString(StandardCharsets.UTF_8), bytes.size.toLong())
    }

    /** Deletes one registered workspace file through the managed workspace boundary. */
    override fun delete(relativePath: String): JavaScriptWorkspaceDeleteResult {
        val record = workspaceFiles.requireFile(null, relativePath)
        return JavaScriptWorkspaceDeleteResult(record.relativePath, workspaceFiles.deleteFile(record.id))
    }
}
