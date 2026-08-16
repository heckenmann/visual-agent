package de.heckenmann.visualagent.protocol

/** MIME type used for editable Visual Agent canvas documents. */
const val CANVAS_MIME_TYPE: String = "application/vnd.visual-agent.canvas+xml"

/** Maximum size accepted for an imported or generated workspace file. */
const val MAX_WORKSPACE_FILE_IMPORT_BYTES: Long = 50L * 1024L * 1024L

/** Maximum image payload returned to the presentation for Markdown rendering. */
const val MAX_MARKDOWN_IMAGE_BYTES: Long = 32L * 1024L * 1024L

/** Maximum width or height accepted for a Markdown image. */
const val MAX_MARKDOWN_IMAGE_DIMENSION: Int = 16_384

/** Maximum decoded pixels accepted for a Markdown image. */
const val MAX_MARKDOWN_IMAGE_PIXELS: Long = 64_000_000L
