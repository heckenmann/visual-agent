package de.heckenmann.visualagent.ui.modal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProtocolErrorCategory

private val ModalShape = RoundedCornerShape(22.dp)
private val LocalModalBodyMaxHeight = compositionLocalOf { 600.dp }
private const val MODAL_TRANSITION_DURATION_MILLIS = 180

/**
 * Modal host that renders all internal dialog variants in one consistent frame.
 *
 * Use cases: UC-0000071.
 *
 * @param modal Current modal request or null when no modal is visible
 * @param onDismiss Callback invoked when the modal is dismissed
 */
@Composable
internal fun composeModalHost(
    modal: ComposeModal?,
    onDismiss: () -> Unit,
) {
    var lastModal by remember { mutableStateOf<ComposeModal?>(null) }
    SideEffect {
        if (modal != null) lastModal = modal
    }
    val displayedModal = modal ?: lastModal ?: return
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = modal != null
    val dismiss = {
        if (displayedModal is ComposeContentModal) displayedModal.onDismiss()
        onDismiss()
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(modal) {
        if (modal != null) focusRequester.requestFocus()
    }
    if (visibility.currentState || visibility.targetState) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0xCC / 255f))
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            dismiss()
                            true
                        } else {
                            false
                        }
                    }.focusRequester(focusRequester)
                    .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = slideInVertically(animationSpec = tween(MODAL_TRANSITION_DURATION_MILLIS)) { it / 8 },
                exit = scaleOut(animationSpec = tween(MODAL_TRANSITION_DURATION_MILLIS), targetScale = 0.96f),
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
                    modalFrame(title = displayedModal.title(), onDismiss = dismiss) {
                        when (displayedModal) {
                            is ComposeConfirmationModal -> confirmationModalContent(modal = displayedModal, onDismiss = dismiss)
                            is ComposeContentModal -> displayedModal.content(dismiss)
                            is ComposeInfoModal -> infoModalContent(modal = displayedModal, onDismiss = dismiss)
                            is ComposeSettingsModal -> Box(modifier = Modifier.padding(22.dp)) { displayedModal.content() }
                            is ComposeErrorModal -> errorModalContent(modal = displayedModal, onDismiss = dismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun modalFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints {
        val maximumHeight = maxHeight * 0.8f
        Card(
            modifier =
                Modifier
                    .heightIn(max = maximumHeight)
                    .widthIn(min = 420.dp, max = 760.dp)
                    .testTag("Internal modal")
                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0x66 / 255f), ModalShape),
            shape = ModalShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, top = 14.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    modalTitle(title)
                    modalSecondaryButton(label = "Close", onClick = onDismiss)
                }
                HorizontalDivider()
                CompositionLocalProvider(LocalModalBodyMaxHeight provides (maximumHeight - 64.dp)) {
                    content()
                }
            }
        }
    }
}

/**
 * Provides the standard scrollable body and persistent, right-aligned action footer for custom dialogs.
 *
 * Dialog content is responsible only for its fields and state, while this layout keeps action placement
 * and overflow behavior consistent across the application.
 */
@Composable
internal fun modalDialogLayout(
    body: @Composable ColumnScope.() -> Unit,
    footer: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Column {
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = LocalModalBodyMaxHeight.current)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = LocalModalBodyMaxHeight.current)
                        .verticalScroll(scrollState)
                        .padding(start = 22.dp, top = 22.dp, end = 36.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = body,
            )
            if (scrollState.maxValue > 0) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).semantics { contentDescription = "Modal scrollbar" },
                )
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = footer,
        )
    }
}

@Composable
private fun confirmationModalContent(
    modal: ComposeConfirmationModal,
    onDismiss: () -> Unit,
) {
    modalDialogLayout(
        body = { Text(text = modal.message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium) },
        footer = {
            modalSecondaryButton(label = modal.dismissDescription, onClick = onDismiss)
            modalPrimaryButton(
                label = modal.confirmDescription,
                onClick = {
                    modal.onConfirm()
                    onDismiss()
                },
            )
        },
    )
}

@Composable
private fun infoModalContent(
    modal: ComposeInfoModal,
    onDismiss: () -> Unit,
) {
    modalDialogLayout(
        body = { Text(text = modal.message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium) },
        footer = { modalPrimaryButton(label = modal.dismissDescription, icon = Icons.Filled.Close, onClick = onDismiss) },
    )
}

@Composable
private fun errorModalContent(
    modal: ComposeErrorModal,
    onDismiss: () -> Unit,
) {
    val color = errorColorForCategory(modal.userError.category)
    modalDialogLayout(
        body = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(imageVector = Icons.Filled.ErrorOutline, contentDescription = null, tint = color)
                Text(
                    text = modal.userError.detail,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        footer = {
            modal.onCopyDetails?.let { onCopy ->
                modalSecondaryButton(label = "Copy details", icon = Icons.Filled.ContentCopy, onClick = onCopy)
            }
            if (modal.userError.retryable && modal.onRetry != null) {
                modalPrimaryButton(
                    label = "Retry",
                    icon = Icons.Filled.Refresh,
                    onClick = {
                        modal.onRetry()
                        onDismiss()
                    },
                )
            }
            modalPrimaryButton(label = modal.dismissDescription, icon = Icons.Filled.Close, onClick = onDismiss)
        },
    )
}

private fun ComposeModal.title(): String =
    when (this) {
        is ComposeConfirmationModal -> title
        is ComposeContentModal -> title
        is ComposeInfoModal -> title
        is ComposeSettingsModal -> title
        is ComposeErrorModal -> userError.summary
    }

@Composable
private fun errorColorForCategory(category: ProtocolErrorCategory): Color {
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        ProtocolErrorCategory.PROVIDER, ProtocolErrorCategory.WORKSPACE -> scheme.tertiary
        ProtocolErrorCategory.CANVAS -> scheme.primary
        ProtocolErrorCategory.TOOL -> scheme.secondary
        ProtocolErrorCategory.PERSISTENCE, ProtocolErrorCategory.UNKNOWN -> scheme.error
    }
}

@Composable
private fun modalTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}
