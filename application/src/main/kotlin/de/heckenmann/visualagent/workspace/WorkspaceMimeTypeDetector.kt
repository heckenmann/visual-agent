package de.heckenmann.visualagent.workspace

import org.apache.tika.Tika
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.fileSize

/** Detects workspace MIME types from bounded file content. */
@Component
class WorkspaceMimeTypeDetector(
    private val tika: Tika = Tika(),
) {
    /** Detects a MIME type from the first bounded content bytes. */
    fun detect(path: Path): String {
        val bytes = Files.newInputStream(path).use { it.readNBytes(MAX_DETECTION_BYTES) }
        return tika.detect(bytes).trim().ifBlank { WorkspaceFilePaths.detectMimeType(path) }
    }

    /** Builds content-derived metadata for a managed workspace file. */
    fun metadata(
        path: Path,
        storedMimeType: String,
    ): WorkspaceMimeTypeInfo {
        val bytes = Files.newInputStream(path).use { it.readNBytes(MAX_DETECTION_BYTES) }
        require(bytes.isNotEmpty()) { "Workspace file is empty" }
        return WorkspaceMimeTypeInfo(
            detectedMimeType = tika.detect(bytes).trim().ifBlank { "application/octet-stream" },
            storedMimeType = storedMimeType,
            sizeBytes = path.fileSize(),
            sha256 = WorkspaceFilePaths.sha256(path),
        )
    }

    private companion object {
        const val MAX_DETECTION_BYTES = 1024 * 1024
    }
}
