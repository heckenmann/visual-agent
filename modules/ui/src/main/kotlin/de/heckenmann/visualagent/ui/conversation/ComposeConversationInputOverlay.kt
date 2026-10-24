package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.IntSize
import de.heckenmann.visualagent.protocol.ConversationInputPlacement

/** Places the translucent composer over the scrollable message history. */
@Composable
internal fun ConversationInputOverlay(
    input: String,
    sending: Boolean,
    isRequestActive: Boolean,
    contextReduced: Boolean,
    interpolationNotice: String = "",
    interpolationBusy: Boolean = false,
    interpolating: Boolean = false,
    onInterpolate: () -> Unit = {},
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    inputPlacement: ConversationInputPlacement,
    onInputPlacementChange: (ConversationInputPlacement) -> Unit,
    inputFocusRequester: FocusRequester,
    ghostText: String,
    ghostCursorVisible: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
    modelSelector: @Composable () -> Unit = {},
) {
    Box(modifier = modifier) {
        ConversationInputCard(
            modelSelector = modelSelector,
            input = input,
            sending = sending,
            isRequestActive = isRequestActive,
            contextReduced = contextReduced,
            interpolationNotice = interpolationNotice,
            interpolationBusy = interpolationBusy,
            interpolating = interpolating,
            onInterpolate = onInterpolate,
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
            onSizeChanged = onSizeChanged,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
