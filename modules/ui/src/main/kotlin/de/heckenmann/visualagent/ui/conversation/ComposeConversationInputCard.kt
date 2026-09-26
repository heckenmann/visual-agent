package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import kotlin.math.min

/** Renders the conversation composer with the shared message-panel styling. */
@Composable
internal fun ConversationInputCard(
    input: String,
    sending: Boolean,
    isRequestActive: Boolean = false,
    contextReduced: Boolean = false,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    inputPlacement: ConversationInputPlacement,
    onInputPlacementChange: (ConversationInputPlacement) -> Unit,
    inputFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onSizeChanged: ((IntSize) -> Unit)? = null,
    ghostText: String = "",
    ghostCursorVisible: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val sizedModifier = onSizeChanged?.let { modifier.onSizeChanged(it) } ?: modifier
    Column(
        modifier =
            sizedModifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.68f))
                .conversationActivityBorder(isRequestActive)
                .animateContentSize(animationSpec = tween(200))
                .padding(bottom = if (isRequestActive) 3.dp else 0.dp),
    ) {
        ConversationInputArea(
            input = input,
            sending = sending,
            contextReduced = contextReduced,
            onInputChange = onInputChange,
            onSend = onSend,
            onCancel = onCancel,
            onClear = onClear,
            inputPlacement = inputPlacement,
            onInputPlacementChange = onInputPlacementChange,
            inputFocusRequester = inputFocusRequester,
            ghostText = ghostText,
            ghostCursorVisible = ghostCursorVisible,
            onFocusChanged = onFocusChanged,
        )
    }
}

@Composable
private fun Modifier.conversationActivityBorder(active: Boolean): Modifier {
    if (!active) return this

    val accent = MaterialTheme.colorScheme.tertiary
    val transition = rememberInfiniteTransition(label = "conversation-input-activity-border")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 16_000, easing = LinearEasing)),
            label = "conversation-input-activity-phase",
        )
    val colorStops =
        Array(49) { index ->
            val position = index / 48f
            val delta = (position - phase + 1f) % 1f
            val distance = min(delta, 1f - delta)
            val highlight = (1f - distance / 0.15f).coerceIn(0f, 1f)
            val alpha = 0.16f + highlight * highlight * 0.84f
            position to accent.copy(alpha = alpha)
        }

    return drawBehind {
        val strokeWidth = 1.5.dp.toPx()
        val inset = strokeWidth / 2
        drawRoundRect(
            brush = Brush.sweepGradient(colorStops = colorStops),
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
    }
}
