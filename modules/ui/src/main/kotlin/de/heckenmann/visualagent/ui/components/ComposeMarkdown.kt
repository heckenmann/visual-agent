@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown
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

/**
 * Renders a Markdown string using the `multiplatform-markdown-renderer` library.
 *
 * This is a thin wrapper around [com.mikepenz.markdown.m3.Markdown] to keep the
 * project's internal naming convention and allow future customization without
 * touching all call sites.
 *
 * @param markdown the raw Markdown text to render (passed 1:1, no pre-normalization)
 * @param modifier optional Compose modifier
 */
@Composable
internal fun ComposeMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    Markdown(
        content = markdown,
        modifier = modifier,
    )
}
