package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/** Changes only replacement slots; initial content and unchanged paragraph spans remain static. */
@Composable
internal fun LiveAnimatedText(
    text: String,
    identity: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    minLines: Int = maxLines,
    terminal: Boolean = false,
    reducedMotion: Boolean = false,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val textStyle =
        remember(style) {
            style.copy(fontFeatureSettings = listOfNotNull(style.fontFeatureSettings, "tnum").joinToString(","))
        }
    val transition = remember(identity) { LiveTextTransition(text) }
    val progress = remember(transition) { Animatable(1f) }
    var pieces by remember(transition) { mutableStateOf<List<LiveTextPiece>?>(null) }
    var preparedText by remember(transition) { mutableStateOf<String?>(null) }
    LaunchedEffect(text, terminal, transition) { transition.offer(text, terminal) }
    LaunchedEffect(transition.revision, reducedMotion) {
        if (!transition.running) return@LaunchedEffect
        if (reducedMotion || currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f) {
            pieces = null
            transition.settle()
            return@LaunchedEffect
        }
        val previous = transition.previous
        val current = transition.current
        val nextPieces = withContext(Dispatchers.Default) { liveTextPieces(previous, current) }
        if (nextPieces == null) {
            pieces = null
            transition.settle()
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        pieces = nextPieces
        preparedText = current
        progress.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        transition.complete()
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.clearAndSetSemantics { this.text = AnnotatedString(transition.latest) }) {
        // Keep the target paragraph after settling: swapping back to plain text reflows unchanged spans.
        val activePieces = pieces.takeIf { transition.running || preparedText == transition.current }
        val paragraph =
            remember(activePieces, textStyle, density, measurer, constraints.maxWidth) {
                activePieces?.let { liveInlineParagraph(it, textStyle, measurer, density, constraints.maxWidth) }
            }
        val shown = if (transition.running && activePieces == null) transition.previous else transition.current
        if (paragraph == null) {
            Text(
                shown,
                style = textStyle,
                color = color,
                minLines = minLines,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = onTextLayout,
            )
        } else {
            Text(
                paragraph.text,
                inlineContent = paragraph.content(textStyle, color) { progress.value },
                style = textStyle,
                color = color,
                minLines = minLines,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = onTextLayout,
            )
        }
    }
}
