package de.heckenmann.visualagent.canvas

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Rect
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle

/** Uses Skia paragraph layout for width-aware wrapping, shaping and font fallback. */
internal object CanvasTextRasterizer {
    fun draw(
        canvas: Canvas,
        text: String,
        bounds: Rect,
        color: Int,
    ) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        FontCollection().use { fonts ->
            fonts.setDefaultFontManager(FontMgr.default)
            TextStyle().use { textStyle ->
                textStyle.fontSize = 16f
                textStyle.color = color
                ParagraphStyle().use { style ->
                    // Match the editor's two-line wrapping/ellipsis contract, not just explicit newline characters.
                    style.maxLinesCount = 2
                    style.ellipsis = "\u2026"
                    style.textStyle = textStyle
                    ParagraphBuilder(style, fonts).use { builder ->
                        builder.addText(text.take(MAX_TEXT_CHARS))
                        builder.build().use { paragraph ->
                            paragraph.layout(bounds.width)
                            val saved = canvas.save()
                            try {
                                canvas.clipRect(bounds)
                                paragraph.paint(canvas, bounds.left, bounds.top)
                            } finally {
                                canvas.restoreToCount(saved)
                            }
                        }
                    }
                }
            }
        }
    }

    private const val MAX_TEXT_CHARS = 32_768
}
