package de.heckenmann.visualagent.canvas

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializes toolkit-neutral editable canvas documents.
 */
internal object CanvasDocumentCodec {
    /** Current document format version. */
    const val VERSION: Int = 1

    /** Encodes the given canvas snapshot as stable UTF-8 text. */
    fun encode(snapshot: CanvasSnapshot): String =
        json.encodeToString(
            CanvasDocument(
                version = VERSION,
                zoomPercent = snapshot.zoomPercent,
                gridVisible = snapshot.gridVisible,
                figures = snapshot.figures,
            ),
        )

    /** Decodes editable canvas state from UTF-8 text. */
    fun decode(text: String): CanvasDocument {
        val document = json.decodeFromString<CanvasDocument>(text)
        require(document.version == VERSION) { "Unsupported canvas document version: ${document.version}" }
        return document.copy(figures = document.figures.mapIndexed { index, figure -> figure.copy(index = index) })
    }

    private val json =
        Json {
            encodeDefaults = true
            prettyPrint = true
            ignoreUnknownKeys = true
        }
}

/**
 * Persisted editable canvas document payload.
 */
@Serializable
internal data class CanvasDocument(
    val version: Int,
    val zoomPercent: Int,
    val gridVisible: Boolean,
    val figures: List<CanvasFigureSnapshot>,
)
