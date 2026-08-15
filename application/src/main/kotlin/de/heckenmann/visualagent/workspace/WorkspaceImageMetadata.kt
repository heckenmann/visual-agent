package de.heckenmann.visualagent.workspace

import java.util.Base64
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes

internal object WorkspaceImageMetadata {
    fun info(path: java.nio.file.Path): WorkspaceImageInfo {
        val dimensions = ImageHeaderReader.dimensions(path)
        return WorkspaceImageInfo(
            width = dimensions.width,
            height = dimensions.height,
            mimeType = WorkspaceFilePaths.detectMimeType(path),
            sizeBytes = path.fileSize(),
            sha256 = WorkspaceFilePaths.sha256(path),
        )
    }

    fun bytes(path: java.nio.file.Path): WorkspaceImageBytes {
        require(path.fileSize() <= MAX_BASE64_BYTES) { "Image is larger than ${MAX_BASE64_BYTES / 1024 / 1024} MB" }
        return WorkspaceImageBytes(
            mimeType = WorkspaceFilePaths.detectMimeType(path),
            base64 = Base64.getEncoder().encodeToString(path.readBytes()),
        )
    }

    private const val MAX_BASE64_BYTES = 8L * 1024L * 1024L
}
