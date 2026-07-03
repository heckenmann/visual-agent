@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableListItemScope

/**
 * Visual drag grip shown in a panel header.
 *
 * The handle is automatically wired to the reorder logic by the enclosing
 * [ReorderableItem] through [draggableHandle].
 *
 * @param primary Whether this handle belongs to the primary chat panel
 */
@Composable
internal fun ReorderableListItemScope.PanelDragHandle(primary: Boolean) {
    Column(
        modifier =
            Modifier
                .width(16.dp)
                .padding(horizontal = 2.dp)
                .draggableHandle(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .size(width = 12.dp, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (primary) Color(0x6650FA7B) else Color(0x558BE9FD)),
            )
        }
    }
}
