package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
                .conversationActivityGlow(isRequestActive)
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
private fun Modifier.conversationActivityGlow(active: Boolean): Modifier {
    val visibility by
        animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            label = "conversation-input-activity-visibility",
        )
    if (visibility == 0f) return this

    val accent = MaterialTheme.colorScheme.tertiary
    val companion = MaterialTheme.colorScheme.secondary
    val transition = rememberInfiniteTransition(label = "conversation-input-activity-light-band")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 9_000, easing = LinearEasing)),
            label = "conversation-input-activity-phase",
        )

    return drawBehind {
        val strokeWidth = 1.25.dp.toPx()
        val inset = strokeWidth / 2
        val backgroundInset = strokeWidth + 1.dp.toPx()
        val halfBandWidth = min(size.width * 0.45f, 300.dp.toPx())
        val centerX = -halfBandWidth + phase * (size.width + halfBandWidth * 2)
        val gradientStart = Offset(centerX - halfBandWidth, -size.height * 0.2f)
        val gradientEnd = Offset(centerX + halfBandWidth, size.height * 1.2f)
        drawRoundRect(
            brush =
                Brush.linearGradient(
                    0f to companion.copy(alpha = 0f),
                    0.27f to companion.copy(alpha = 0.06f * visibility),
                    0.5f to accent.copy(alpha = 0.17f * visibility),
                    0.73f to companion.copy(alpha = 0.06f * visibility),
                    1f to accent.copy(alpha = 0f),
                    start = gradientStart,
                    end = gradientEnd,
                ),
            topLeft = Offset(backgroundInset, backgroundInset),
            size = Size(size.width - backgroundInset * 2, size.height - backgroundInset * 2),
            cornerRadius = CornerRadius((12.dp - 1.dp).toPx()),
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.09f * visibility),
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawRoundRect(
            brush =
                Brush.linearGradient(
                    0f to companion.copy(alpha = 0f),
                    0.32f to companion.copy(alpha = 0.24f * visibility),
                    0.5f to accent.copy(alpha = 0.62f * visibility),
                    0.68f to companion.copy(alpha = 0.24f * visibility),
                    1f to accent.copy(alpha = 0f),
                    start = gradientStart,
                    end = gradientEnd,
                ),
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
    }
}
