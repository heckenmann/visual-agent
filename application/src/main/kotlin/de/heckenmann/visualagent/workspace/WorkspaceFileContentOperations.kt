package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.file.Path
import kotlin.io.path.readText

/** Performs content reads for the workspace file facade. */
internal class WorkspaceFileContentOperations(
    private val store: WorkspaceFileStore,
    private val mimeDetector: WorkspaceMimeTypeDetector,
    private val resolvePath: (String) -> Path,
) {
    /** Computes the current SHA-256 hash for a managed file. */
    fun hash(record: WorkspaceFileRecord): String = WorkspaceFilePaths.sha256(resolvePath(record.relativePath))

    /** Reads bounded UTF-8 text from a managed file. */
    fun readText(record: WorkspaceFileRecord): String = resolvePath(record.relativePath).readText(Charsets.UTF_8).take(MAX_TEXT_CHARS)

    /** Extracts and caches text from a managed PDF. */
    fun extractPdfText(record: WorkspaceFileRecord): WorkspaceFileText {
        record.extractedText?.let { return WorkspaceFileText(it.take(MAX_TEXT_CHARS), cached = true) }
        val text =
            Loader.loadPDF(resolvePath(record.relativePath).toFile()).use { document ->
                PDFTextStripper().getText(document).trim().take(MAX_TEXT_CHARS)
            }
        store.saveWorkspaceFile(record.copy(extractedText = text, updatedAt = java.time.Instant.now()))
        return WorkspaceFileText(text, cached = false)
    }

    /** Reads image dimensions and metadata. */
    fun imageInfo(record: WorkspaceFileRecord): WorkspaceImageInfo = WorkspaceImageMetadata.info(resolvePath(record.relativePath))

    /** Returns bounded base64 bytes for image/tool transport. */
    fun imageBytes(record: WorkspaceFileRecord): WorkspaceImageBytes = WorkspaceImageMetadata.bytes(resolvePath(record.relativePath))

    /** Detects a managed file MIME type from bounded content bytes. */
    fun detectMimeType(record: WorkspaceFileRecord): WorkspaceMimeTypeInfo =
        mimeDetector.metadata(resolvePath(record.relativePath), record.mimeType)

    /** Renders one PDF page and delegates persistence of the generated preview. */
    fun renderPdfPage(
        record: WorkspaceFileRecord,
        page: Int,
        createManagedFile: (String, String, ByteArray, String?) -> WorkspaceFileRecord,
    ): WorkspaceFileRecord {
        require(page >= 1) { "PDF page must be >= 1" }
        val path = resolvePath(record.relativePath)
        val pageText =
            Loader.loadPDF(path.toFile()).use { document ->
                require(page <= document.numberOfPages) { "PDF page must be <= ${document.numberOfPages}" }
                PDFTextStripper()
                    .apply {
                        startPage = page
                        endPage = page
                    }.getText(document)
                    .trim()
            }
        val requestedName = "${path.fileName.toString().substringBeforeLast('.', path.fileName.toString())}-page-$page.png"
        return createManagedFile(
            "generated",
            requestedName,
            PdfPagePreviewRenderer.render(path.fileName.toString(), page, pageText),
            "image/png",
        )
    }

    private companion object {
        const val MAX_TEXT_CHARS = 120_000
    }
}
