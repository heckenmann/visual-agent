package de.heckenmann.visualagent.canvas

import java.nio.file.Path

/** Resolves legacy absolute references only within the managed root, then reads a registered, bounded file. */
internal fun InMemoryCanvasService.readCaptureImage(reference: String): ByteArray {
    val root = workspaceFileService.workspaceRoot()
    val path = Path.of(reference)
    val relative =
        if (path.isAbsolute) {
            require(path.normalize().startsWith(root)) { "Canvas image is outside the managed workspace" }
            root.relativize(path.normalize()).toString()
        } else {
            reference
        }
    val record = workspaceFileService.requireFile(null, relative)
    return workspaceFileService.readBytes(record, CanvasCaptureLimits.MAX_IMAGE_BYTES)
}
