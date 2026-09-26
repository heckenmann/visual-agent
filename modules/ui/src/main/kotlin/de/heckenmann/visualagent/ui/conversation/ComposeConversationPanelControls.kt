package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandCircleDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@Composable
internal fun ScrollToBottomButton(
    onClick: () -> Unit,
    hasNewMessages: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (hasNewMessages) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.semantics { contentDescription = "Show new messages" },
        ) {
            Icon(Icons.Filled.ExpandCircleDown, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New messages")
        }
        return
    }
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
    contextReduced: Boolean = false,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    inputPlacement: ConversationInputPlacement = ConversationInputPlacement.CONVERSATION_MESSAGE,
    onInputPlacementChange: (ConversationInputPlacement) -> Unit = {},
    inputFocusRequester: FocusRequester,
    ghostText: String = "",
    ghostCursorVisible: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Clear conversation",
                onClick = onClear,
                modifier = Modifier.size(40.dp),
            )
            ActionIconButton(
                icon = Icons.Filled.PushPin,
                description =
                    if (inputPlacement == ConversationInputPlacement.FIXED) {
                        "Use conversation message input"
                    } else {
                        "Pin input field to panel"
                    },
                onClick = {
                    onInputPlacementChange(
                        if (inputPlacement == ConversationInputPlacement.FIXED) {
                            ConversationInputPlacement.CONVERSATION_MESSAGE
                        } else {
                            ConversationInputPlacement.FIXED
                        },
                    )
                },
                selected = inputPlacement == ConversationInputPlacement.FIXED,
                modifier = Modifier.size(40.dp),
            )
        }
        AnimatedVisibility(
            visible = contextReduced,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
        ) {
            Text(
                text = "Recent context or tool details were omitted to fit the model's token limit.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = null,
            placeholder = {
                if (ghostText.isBlank()) {
                    Text("Type here…")
                } else {
                    Row(modifier = Modifier.clearAndSetSemantics {}) {
                        Text(
                            text = ghostText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        )
                        if (ghostCursorVisible) {
                            Text(
                                text = "|",
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            },
            minLines = 1,
            maxLines = 5,
            trailingIcon = {
                if (sending) {
                    ActionIconButton(
                        icon = Icons.Filled.Stop,
                        description = "Cancel response",
                        onClick = onCancel,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    ActionIconButton(
                        icon = Icons.AutoMirrored.Filled.Send,
                        description = if (contextReduced) "Send message; context was reduced" else "Send message",
                        onClick = onSend,
                        enabled = input.isNotBlank(),
                        modifier = Modifier.size(40.dp),
                        tint = if (contextReduced) MaterialTheme.colorScheme.error else null,
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .focusRequester(inputFocusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
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
