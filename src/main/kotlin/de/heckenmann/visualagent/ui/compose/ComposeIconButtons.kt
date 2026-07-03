@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionTooltip(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(description)
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * Small icon-only action button used across the workspace.
 *
 * Supports an optional selected state that visually highlights the button
 * so users can identify an active mode or toggle state at a glance.
 *
 * @param icon Icon vector to display
 * @param description Tooltip and accessibility description
 * @param onClick Click handler
 * @param modifier Compose modifier
 * @param enabled Whether the button accepts input
 * @param selected Whether the button shows the active/mode-selected highlight
 * @param iconSize Icon size
 * @param onLongClick Optional long-click handler
 */
@Composable
internal fun ActionIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    iconSize: Dp = 17.dp,
    onLongClick: (() -> Unit)? = null,
) {
    val backgroundColor = if (selected) Color(0x338BE9FD) else Color.Transparent
    val iconTint = if (selected) Color(0xFF8BE9FD) else LocalContentColor.current
    ActionTooltip(description = description) {
        Box(
            modifier =
                modifier
                    .defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
                    .background(backgroundColor, RoundedCornerShape(6.dp))
                    .alpha(if (enabled) 1f else 0.38f)
                    .combinedClickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(iconSize),
                tint = if (enabled) iconTint else LocalContentColor.current,
            )
        }
    }
}
