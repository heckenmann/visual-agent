@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.config.ConversationInputPlacement
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

/** Renders the conversation composer with the shared message-panel styling. */
@Composable
internal fun ConversationInputCard(
    input: String,
    sending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    inputPlacement: ConversationInputPlacement,
    onInputPlacementChange: (ConversationInputPlacement) -> Unit,
    inputFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onSizeChanged: ((IntSize) -> Unit)? = null,
) {
    val sizedModifier = onSizeChanged?.let { modifier.onSizeChanged(it) } ?: modifier
    Column(
        modifier =
            sizedModifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .animateContentSize(animationSpec = tween(200)),
    ) {
        ConversationInputArea(
            input = input,
            sending = sending,
            onInputChange = onInputChange,
            onSend = onSend,
            onCancel = onCancel,
            onClear = onClear,
            inputPlacement = inputPlacement,
            onInputPlacementChange = onInputPlacementChange,
            inputFocusRequester = inputFocusRequester,
        )
    }
}
