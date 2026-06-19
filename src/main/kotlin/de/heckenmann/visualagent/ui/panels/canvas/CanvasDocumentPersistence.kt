package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.knowledge.PreferenceStore
import org.jhotdraw8.draw.figure.Drawing
import org.jhotdraw8.draw.figure.SimpleLayeredDrawing
import org.jhotdraw8.draw.io.DefaultFigureFactory
import org.jhotdraw8.draw.io.SimpleFigureIdFactory
import org.jhotdraw8.draw.io.SimpleXmlReader
import org.jhotdraw8.draw.io.SimpleXmlWriter
import org.jhotdraw8.fxbase.concurrent.SimpleWorkState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Persists the complete JHotDraw document in the application preference store.
 */
internal class CanvasDocumentPersistence(
    private val preferenceStore: PreferenceStore,
) {
    fun load(): SimpleLayeredDrawing? {
        val xml = preferenceStore.getPreference(CANVAS_DOCUMENT_KEY)?.takeIf(String::isNotBlank) ?: return null
        val factory = DefaultFigureFactory()
        val reader = SimpleXmlReader(factory, SimpleFigureIdFactory(), null)
        return reader
            .read(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), null, null, SimpleWorkState())
            as? SimpleLayeredDrawing
    }

    fun save(drawing: Drawing) {
        val output = ByteArrayOutputStream()
        val writer = SimpleXmlWriter(DefaultFigureFactory(), SimpleFigureIdFactory())
        writer.write(output, null, drawing, SimpleWorkState())
        preferenceStore.setPreference(CANVAS_DOCUMENT_KEY, output.toString(Charsets.UTF_8))
    }

    private companion object {
        const val CANVAS_DOCUMENT_KEY = "canvas.document.xml"
    }
}
