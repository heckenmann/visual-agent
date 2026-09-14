package de.heckenmann.visualagent.ui.modal

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProtocolErrorCategory

internal val LocalModalBodyMaxHeight = compositionLocalOf { 600.dp }

/** Renders one modal's type-specific content inside the shared modal frame. */
@Composable
internal fun ComposeModalContent(
    modal: ComposeModal,
    onDismiss: () -> Unit,
) {
    when (modal) {
        is ComposeConfirmationModal -> confirmationModalContent(modal, onDismiss)
        is ComposeContentModal -> modal.content(onDismiss)
        is ComposeInfoModal -> infoModalContent(modal, onDismiss)
        is ComposeSettingsModal -> Box(modifier = Modifier.padding(22.dp)) { modal.content() }
        is ComposeErrorModal -> errorModalContent(modal, onDismiss)
    }
}

/**
 * Provides the standard scrollable body and persistent, right-aligned action footer for custom dialogs.
 *
 * Dialog content is responsible only for its fields and state, while this layout keeps action placement
 * and overflow behavior consistent across the application.
 */
@Composable
fun ModalDialogLayout(
    body: @Composable ColumnScope.() -> Unit,
    footer: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Column {
        Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
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
    ModalDialogLayout(
        body = { Text(modal.message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium) },
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
    ModalDialogLayout(
        body = { Text(modal.message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium) },
        footer = { modalPrimaryButton(label = modal.dismissDescription, icon = Icons.Filled.Close, onClick = onDismiss) },
    )
}

@Composable
private fun errorModalContent(
    modal: ComposeErrorModal,
    onDismiss: () -> Unit,
) {
    val color = errorColorForCategory(modal.userError.category)
    ModalDialogLayout(
        body = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = color)
                Text(modal.userError.detail, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
        },
        footer = {
            modal.onCopyDetails?.let { onCopy ->
                modalSecondaryButton(
                    label = "Copy details",
                    icon = Icons.Filled.ContentCopy,
                    onClick = {
                        onCopy()
                        onDismiss()
                    },
                )
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
