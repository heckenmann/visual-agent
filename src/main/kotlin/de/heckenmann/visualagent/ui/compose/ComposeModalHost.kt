@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Modal host that renders confirmation, content, and info dialogs.
 *
 * Use cases: UC-0000071.
 *
 * @param modal Current modal request or null when no modal is visible
 * @param onDismiss Callback invoked when the modal is dismissed
 */
@Composable
internal fun ComposeModalHost(
    modal: ComposeModal?,
    onDismiss: () -> Unit,
) {
    if (modal == null) return
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xCC191A21))
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ModalCard {
            when (modal) {
                is ComposeConfirmationModal -> ConfirmationModalContent(modal = modal, onDismiss = onDismiss)
                is ComposeContentModal -> ContentModalContent(modal = modal, onDismiss = onDismiss)
                is ComposeInfoModal -> InfoModalContent(modal = modal, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun ModalCard(content: @Composable () -> Unit) {
    Card(
        modifier =
            Modifier
                .widthIn(min = 320.dp, max = 560.dp)
                .border(1.dp, Color(0x6650FA7B), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF282A36)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ConfirmationModalContent(
    modal: ComposeConfirmationModal,
    onDismiss: () -> Unit,
) {
    ModalTitle(modal.title)
    Text(text = modal.message, color = Color(0xFFE6E6E6), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        ActionIconButton(
            icon = Icons.Filled.Close,
            description = modal.dismissDescription,
            onClick = onDismiss,
        )
        ActionIconButton(
            icon = Icons.Filled.Check,
            description = modal.confirmDescription,
            onClick = {
                modal.onConfirm()
                onDismiss()
            },
        )
    }
}

@Composable
private fun InfoModalContent(
    modal: ComposeInfoModal,
    onDismiss: () -> Unit,
) {
    ModalTitle(modal.title)
    Text(
        text = modal.message,
        color = Color(0xFFE6E6E6),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        ActionIconButton(
            icon = Icons.Filled.Close,
            description = modal.dismissDescription,
            onClick = onDismiss,
        )
    }
}

@Composable
private fun ContentModalContent(
    modal: ComposeContentModal,
    onDismiss: () -> Unit,
) {
    ModalTitle(modal.title)
    modal.content(onDismiss)
}

@Composable
private fun ModalTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFFF8F8F2),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}
