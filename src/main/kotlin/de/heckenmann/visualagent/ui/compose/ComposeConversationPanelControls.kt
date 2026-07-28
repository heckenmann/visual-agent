@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandCircleDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

@Composable
internal fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionIconButton(
        icon = Icons.Filled.ExpandCircleDown,
        description = "Scroll to latest message",
        onClick = onClick,
        modifier =
            modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small)
                .padding(8.dp),
        iconSize = 26.dp,
    )
}

@Composable
internal fun ConversationInputArea(
    input: String,
    sending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    inputFocusRequester: FocusRequester,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Message",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Clear conversation",
                onClick = onClear,
                modifier = Modifier.size(24.dp),
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = null,
            placeholder = { Text("Type a message…") },
            minLines = 1,
            maxLines = 5,
            trailingIcon = {
                if (sending) {
                    ActionIconButton(
                        icon = Icons.Filled.Stop,
                        description = "Cancel response",
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    ActionIconButton(
                        icon = Icons.AutoMirrored.Filled.Send,
                        description = "Send message",
                        onClick = onSend,
                        enabled = input.isNotBlank(),
                        modifier = Modifier.size(32.dp),
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .focusRequester(inputFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                            onSend()
                            true
                        } else {
                            false
                        }
                    },
        )
    }
}

@Composable
internal fun PulsingDots() {
    val transition = rememberInfiniteTransition(label = "streaming")
    val offsets = listOf(0, 160, 320)
    val alphas =
        offsets.map { delayMs ->
            val animatedAlpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = STREAMING_DOT_ANIMATION_CYCLE_MS),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                        initialStartOffset =
                            androidx.compose.animation.core
                                .StartOffset(offsetMillis = delayMs),
                    ),
                label = "streaming-dot-$delayMs",
            )
            animatedAlpha
        }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        alphas.forEach { alpha ->
            Box(
                modifier =
                    Modifier
                        .size(4.dp)
                        .alpha(alpha)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
            )
        }
    }
}

private const val STREAMING_DOT_ANIMATION_CYCLE_MS = 700
