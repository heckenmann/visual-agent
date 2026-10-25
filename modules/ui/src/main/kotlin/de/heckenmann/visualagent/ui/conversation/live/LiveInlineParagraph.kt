package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density

/** A native paragraph with stable target geometry throughout and after each replacement. */
internal data class LiveInlineParagraph(
    val text: AnnotatedString,
    val slots: Map<String, LiveInlineSlot>,
) {
    /** Creates native inline content; animated properties are read only during layer drawing. */
    fun content(
        style: TextStyle,
        color: Color,
        progress: () -> Float,
    ): Map<String, InlineTextContent> =
        slots.mapValues { (_, slot) ->
            InlineTextContent(slot.placeholder) {
                Box(Modifier.fillMaxSize().clipToBounds().clearAndSetSemantics {}) {
                    FlipLiveWord(slot.piece.previous, style, color, outgoing = true, progress)
                    FlipLiveWord(slot.piece.current, style, color, outgoing = false, progress)
                }
            }
        }
}

/** Reserved native text-layout geometry, independent of animation frame values. */
internal data class LiveInlineSlot(
    val piece: LiveTextPiece,
    val placeholder: Placeholder,
)

/** Keeps paragraph layout native; words wider than a line use static native wrapping rather than oversized slots. */
internal fun liveInlineParagraph(
    pieces: List<LiveTextPiece>,
    style: TextStyle,
    measurer: TextMeasurer,
    density: Density,
    maxWidth: Int,
): LiveInlineParagraph? {
    val slots = mutableMapOf<String, LiveInlineSlot>()
    var requiresNativeWrapping = false
    val lineHeight = measurer.measure("Mg", style, softWrap = false, maxLines = 1).size.height
    val paragraph =
        buildAnnotatedString {
            pieces.forEachIndexed { index, piece ->
                if (!piece.changed || piece.current.isEmpty() || (piece.previous.isBlank() && piece.current.isBlank())) {
                    append(piece.current)
                } else {
                    val width =
                        measurer
                            .measure(piece.current, style, softWrap = false, maxLines = 1)
                            .size.width
                            .coerceAtLeast(1)
                    if (width > maxWidth) requiresNativeWrapping = true
                    val id = "live-word-$index"
                    val placeholder =
                        with(density) {
                            Placeholder(width.toFloat().toSp(), lineHeight.toFloat().toSp(), PlaceholderVerticalAlign.TextCenter)
                        }
                    slots[id] = LiveInlineSlot(piece, placeholder)
                    appendInlineContent(id, piece.current.ifEmpty { "\u200B" })
                }
            }
        }
    return if (requiresNativeWrapping) null else LiveInlineParagraph(paragraph, slots)
}

@Composable
private fun FlipLiveWord(
    text: String,
    style: TextStyle,
    color: Color,
    outgoing: Boolean,
    progress: () -> Float,
) {
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
                val fraction = if (outgoing) (progress() * 2).coerceIn(0f, 1f) else (progress() * 2 - 1).coerceIn(0f, 1f)
                rotationX = if (outgoing) -80f * fraction else 80f * (1 - fraction)
                alpha = if (outgoing) 1 - fraction else fraction
                cameraDistance = 16f * density
            },
    )
}
