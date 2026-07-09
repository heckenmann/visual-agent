@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.error.ErrorCategory
import de.heckenmann.visualagent.error.UserFacingError

/**
 * Inline error banner that displays a structured [UserFacingError] with optional retry and copy
 * actions.
 *
 * @param userError Structured error to display
 * @param onRetry Called when the user taps the retry action; shown when [UserFacingError.retryable]
 *   is true
 * @param onCopyDetails Called when the user taps the copy action; shown when non-null
 * @param modifier Modifier applied to the banner root
 */
@Composable
internal fun ErrorBanner(
    userError: UserFacingError,
    onRetry: (() -> Unit)? = null,
    onCopyDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val color = errorBannerColorForCategory(userError.category)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color(0x22282536), RoundedCornerShape(10.dp))
                .border(1.dp, color, RoundedCornerShape(10.dp))
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = color,
            modifier = Modifier.align(Alignment.Top),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = userError.summary,
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = userError.detail,
                color = Color(0xFFE6E6E6),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onCopyDetails?.let { onCopy ->
                ActionIconButton(
                    icon = Icons.Filled.ContentCopy,
                    description = "Copy error details",
                    onClick = onCopy,
                )
            }
            if (userError.retryable && onRetry != null) {
                ActionIconButton(
                    icon = Icons.Filled.Refresh,
                    description = "Retry",
                    onClick = onRetry,
                )
            }
        }
    }
}

private fun errorBannerColorForCategory(category: ErrorCategory): Color =
    when (category) {
        ErrorCategory.PROVIDER -> Color(0xFFFFB86C)
        ErrorCategory.WORKSPACE -> Color(0xFF8BE9FD)
        ErrorCategory.CANVAS -> Color(0xFFBD93F9)
        ErrorCategory.TOOL -> Color(0xFFFF79C6)
        ErrorCategory.PERSISTENCE -> Color(0xFFFF5555)
        ErrorCategory.UNKNOWN -> Color(0xFFFF5555)
    }
