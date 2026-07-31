@file:Suppress("FunctionName", "ktlint:standard:import-ordering")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Holds the scrollbar adapter contributed by the scrollable body of one workspace panel.
 */
internal class PanelScrollbarController {
    var adapter: ScrollbarAdapter? by mutableStateOf(null)
        private set

    /** Updates the adapter rendered by the enclosing workspace panel. */
    fun updateAdapter(next: ScrollbarAdapter?) {
        adapter = next
    }
}

private val LocalPanelScrollbarController = staticCompositionLocalOf<PanelScrollbarController?> { null }

/**
 * Provides a workspace-panel scrollbar controller to the nested panel body.
 *
 * The enclosing panel chrome renders the scrollbar, while a scrollable child only
 * registers its adapter through [RegisterPanelScrollbar].
 */
@Composable
internal fun PanelScrollbarHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val controller = remember { PanelScrollbarController() }
    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPanelScrollbarController provides controller) {
            content()
            controller.adapter?.let { adapter ->
                VerticalScrollbar(
                    adapter = adapter,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                            .semantics { contentDescription = "Panel scrollbar" },
                )
            }
        }
    }
}

/** Registers a scroll adapter with the enclosing [PanelScrollbarHost]. */
@Composable
internal fun RegisterPanelScrollbar(adapter: ScrollbarAdapter) {
    val controller = LocalPanelScrollbarController.current
    DisposableEffect(controller, adapter) {
        controller?.updateAdapter(adapter)
        onDispose {
            if (controller?.adapter === adapter) controller.updateAdapter(null)
        }
    }
}

/** Registers a regular vertical [ScrollState] with the enclosing panel chrome. */
@Composable
internal fun RegisterPanelVerticalScrollbar(scrollState: ScrollState) {
    RegisterPanelScrollbar(rememberScrollbarAdapter(scrollState))
}
